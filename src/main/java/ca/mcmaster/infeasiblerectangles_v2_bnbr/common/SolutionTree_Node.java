/*

 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ca.mcmaster.infeasiblerectangles_v2_bnbr.common;

import ca.mcmaster.infeasiblerectangles_v2_bnbr.BNBR_Driver;
import static ca.mcmaster.infeasiblerectangles_v2_bnbr.Constants.*;
import static ca.mcmaster.infeasiblerectangles_v2_bnbr.Parameters.RECTS_TO_BE_COLLECTED_PER_CONSTRAINT;
import static ca.mcmaster.infeasiblerectangles_v2_bnbr.Parameters.REPLENISH_THESHOLD_FRACTION;
import ca.mcmaster.infeasiblerectangles_v2_bnbr.collection.Analytic_RectangleCollector;
import static java.lang.System.exit;
import java.util.*;
import java.util.Map;
import java.util.Map.Entry;

/**
 *
 * @author tamvadss
 */
public class SolutionTree_Node extends Rectangle{
    
    public static long NODE_ID = ZERO;
    
    public long myId =-ONE;
    
    
    //list of infeasible rectangles that are compatible with this leaf node
    //key is lp relax value
    public List<TreeMap <Double, List<Rectangle>> > myInfeasibleRectanglesList = null;  // per constraint
    public  List<Boolean> areAllRectsCollectedForThisConstraint = new ArrayList<Boolean>();
    
    public SolutionTree_Node( List <String> zeroFixed,  List <String> oneFixed, List<Boolean> areAllRectsCollectedForThisConstraint){
        super( zeroFixed,   oneFixed) ;
        myId =NODE_ID++;
        myInfeasibleRectanglesList = new ArrayList <TreeMap <Double, List<Rectangle>> > ();
        for (LowerBoundConstraint lbc : BNBR_Driver.mipConstraintList) {
             myInfeasibleRectanglesList.add(new TreeMap <Double, List<Rectangle>>() );
             //areAllRectsCollectedForThisConstraint.add(false);
        }
        this.areAllRectsCollectedForThisConstraint = areAllRectsCollectedForThisConstraint;
                
    }
    
    public SolutionTree_Node( List <String> zeroFixed,  List <String> oneFixed ){
        super( zeroFixed,   oneFixed) ;
        myId =NODE_ID++;
        myInfeasibleRectanglesList = new ArrayList <TreeMap <Double, List<Rectangle>> > ();
        for (LowerBoundConstraint lbc : BNBR_Driver.mipConstraintList) {
             myInfeasibleRectanglesList.add(new TreeMap <Double, List<Rectangle>>() );
             areAllRectsCollectedForThisConstraint.add(false);
        }       
    }
    
    public String toString (){
        return "Node id " + myId + " " + super.toString() ; //+ printmyInfeasibleRectanglesList(); //+ " infeas map size " + myInfeasibleRectangles.size();
    }
    
       //invoke this method before inserting into active leaf list for enum solver
    //
    //this code is duplicated and needs to be moved to the proper location
    //
    //returns negative freq
    public int getRefcount_of_HighestFreqVar( ) {
        List<Rectangle> rectanglesToConsider = new ArrayList<Rectangle> ();
         
        //for every constraint, see if it has rects at the best lp
        double bestLPAmongAllInfeasibleRectangles = this.getBestLPAmongAllInfeasibleRectangles();
        
        for (Map <Double, List<Rectangle>> rectMap: myInfeasibleRectanglesList) {
            List<Rectangle> rects = rectMap.get( bestLPAmongAllInfeasibleRectangles);
            if (null!=rects) {
                rectanglesToConsider.addAll(rects );
            }
        }
        
        //find the var with the highest refcount and return its refcount
        
        //collect refcounts into this map
        Map<String, Integer> varRefCountMap = new HashMap<String, Integer> () ;
        
        List<VariableCoefficientTuple> variablesUsedForBranchingInThisRectangle =null;
        for (Rectangle rect: rectanglesToConsider){
            variablesUsedForBranchingInThisRectangle = getVariablesUsedForBranchingInThisRectangle (rect);
            if (variablesUsedForBranchingInThisRectangle.size()>ZERO) {
                this.updateVariableRefCounts(varRefCountMap, variablesUsedForBranchingInThisRectangle) ;
            }else {
                System.err.print("trying to branch a node which should have been marked infeasible" );
                exit(ONE);
            }
        }
        
        //arrange vars in refcount order
        //note we multiply by -1 to get in decreasing order
        Map< Integer,List<String>> orderedVarRefCountMap = new TreeMap< Integer,List<String>> () ;
        for (Map.Entry<String, Integer> entry : varRefCountMap.entrySet()) {
            List<String> currentList = orderedVarRefCountMap.get(-entry.getValue());
            if (null==currentList) currentList=new ArrayList<String>();
            currentList.add(entry.getKey() );
            orderedVarRefCountMap.put(-entry.getValue(), currentList);
            
        }
        
        return Collections.min(orderedVarRefCountMap.keySet());
    }
    
    /*public String printmyInfeasibleRectanglesList() {
        String result = "";
        for (TreeMap <Double, List<Rectangle>> map : this.myInfeasibleRectanglesList){
            for (Entry<Double, List<Rectangle>> entry : map.entrySet()) {
                result += ""+ entry.getKey() + "\n";
                for (Rectangle rect : entry.getValue()) {
                    result += ""+ rect.toString()+"\n";
                }
            }
        }
        return result ;
    }*/
    
    public void setInfeasibleRectangles (int constraintNumber, TreeMap <Double, List<Rectangle>> newMap) {
        myInfeasibleRectanglesList.set(constraintNumber , newMap);
    }
    
    //for every constraint in this mip, replenish infeasible rects till bucket has RECTS_TO_BE_COLLECTED_PER_CONSTRAINT rects or no more left
    public void replenishRectangles () {
        int constraintNumber = -ONE;
        for (TreeMap <Double, List<Rectangle>> rectMap : myInfeasibleRectanglesList) {
            
            //this is the map of infeasible rects for the jth constraint
            constraintNumber ++;
           
            if (    ! rectMap.isEmpty()  || areAllRectsCollectedForThisConstraint.get( constraintNumber))             {
                //System.out.println(" will not replenish");
                continue ;// do not replenish
            }  
              
            System.err.println(" replenishRectangles for " +constraintNumber) ;
                         
            Analytic_RectangleCollector rectangleCollector = new  Analytic_RectangleCollector (this,BNBR_Driver.mipConstraintList.get(constraintNumber) );
            boolean allCollected = rectangleCollector.collect( RECTS_TO_BE_COLLECTED_PER_CONSTRAINT);
            //add all the new rects , if any, to the existing map for this constraint
            for(Map.Entry<Double, List<Rectangle>> entry : rectangleCollector.collectedFeasibleRectangles.entrySet()){
                //add into rectMap
                List<Rectangle> currentList = rectMap.get( entry.getKey());
                if (null==currentList)  currentList = new ArrayList<Rectangle> ();
                currentList.addAll( entry.getValue());
                rectMap.put( entry.getKey(), currentList);
            }
            areAllRectsCollectedForThisConstraint.set(constraintNumber , allCollected);
            if (!allCollected) {
                // System.out.println(" not all collected for "+BNBR_Driver.mipConstraintList.get(constraintNumber).name) ;
            }
        }
        
         
    }

    public  List<Rectangle> getAllInfeasibleRectangles () {
        List<Rectangle> result = new ArrayList<Rectangle> ();
        for (Map <Double, List<Rectangle>> rectMap: myInfeasibleRectanglesList) {
            for (List<Rectangle> rects: rectMap.values()) {
                 result.addAll( rects);
            }            
        }
        return  result;
    }
        
    public  double getBestLPAmongAllInfeasibleRectangles () {
        double lowest = Double.MAX_VALUE;
        
        for (Map <Double, List<Rectangle>> rectMap: myInfeasibleRectanglesList) {
            double thisLowest = rectMap.isEmpty() ?  Double.MAX_VALUE: Collections.min( rectMap.keySet());
            if (lowest >thisLowest) lowest = thisLowest ;
        }
        
        return lowest;
    }
    
    //duplicated code, move to proper location
    private void updateVariableRefCounts(Map<String, Integer>  varRefCountMap, List<VariableCoefficientTuple>  variablesUsedForBranchingInThisRectangle){
        for (VariableCoefficientTuple tuple : variablesUsedForBranchingInThisRectangle) {
            int currentCount = varRefCountMap.get(tuple.varName) == null ? ZERO: varRefCountMap.get(tuple.varName );
            varRefCountMap.put(tuple.varName, ONE+currentCount);
        }
    }
    //duplictaded code    
    //get vars used , consider only free vars
    private  List<VariableCoefficientTuple> getVariablesUsedForBranchingInThisRectangle(Rectangle rect){
        List<VariableCoefficientTuple> variablesUsedForBranchingInThisRectangle = new ArrayList<VariableCoefficientTuple> ();
        
        for (String var : rect.zeroFixedVariables) {
            if (!this.zeroFixedVariables.contains(var) && !this.oneFixedVariables.contains(var)) 
                variablesUsedForBranchingInThisRectangle.add(new VariableCoefficientTuple(var, ZERO) );
        }
        for (String var : rect.oneFixedVariables){
            //if (!variablesUsedForBranchingInThisRectangle.contains(var)) { //always true
                 if (!this.zeroFixedVariables.contains(var) && !this.oneFixedVariables.contains(var)) {
                     variablesUsedForBranchingInThisRectangle.add(new VariableCoefficientTuple(var, ONE));
                 }
            //}
        }
         
        return variablesUsedForBranchingInThisRectangle;
    }
    
}
