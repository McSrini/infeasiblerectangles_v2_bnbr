/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ca.mcmaster.infeasiblerectangles_v2_bnbr.common.utils;

import ca.mcmaster.infeasiblerectangles_v2_bnbr.BNBR_Driver;
import ca.mcmaster.infeasiblerectangles_v2_bnbr.common.Rectangle;
import static ca.mcmaster.infeasiblerectangles_v2_bnbr.Constants.*;
import ca.mcmaster.infeasiblerectangles_v2_bnbr.common.SolutionTree_Node;
import ca.mcmaster.infeasiblerectangles_v2_bnbr.common.VariableCoefficientTuple;
import ca.mcmaster.infeasiblerectangles_v2_bnbr.solvers.analytic.BNBR_Solver;
import static java.lang.System.exit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.*;
import java.util.List;
import java.util.Map;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;
import org.apache.log4j.RollingFileAppender;

/**
 *
 * @author tamvadss
 */
public class BranchingVariableSuggestor {
    
    private  List<TreeMap <Double, List<Rectangle>> > myInfeasibleRectanglesList = null;  // per constraint
    private List<String> treeNode_zeroFixedVariables;
    private   List<String>  treeNode_oneFixedVariables;
    private double bestLPValueAmongInfeasibleRects = ZERO;
    
    private static Logger logger=Logger.getLogger(BranchingVariableSuggestor.class);    
    static {
        logger.setLevel(Level.OFF);
        PatternLayout layout = new PatternLayout("%5p  %d  %F  %L  %m%n");     
        try {
            logger.addAppender(new  RollingFileAppender(layout,LOG_FOLDER+BranchingVariableSuggestor.class.getSimpleName()+ LOG_FILE_EXTENSION));
            logger.setAdditivity(false);
        } catch (Exception ex) {
            ///
            System.err.println("Exit: unable to initialize logging");       
            exit(1);
        }
        
        
    }
    
    
    public BranchingVariableSuggestor ( SolutionTree_Node treeNode, double bestLPAmongAllInfeasibleRectangles) {
        this.myInfeasibleRectanglesList = treeNode.myInfeasibleRectanglesList;
        this.treeNode_oneFixedVariables=treeNode.oneFixedVariables;
        this.treeNode_zeroFixedVariables = treeNode.zeroFixedVariables;
        this.bestLPValueAmongInfeasibleRects =bestLPAmongAllInfeasibleRectangles;
    }
    
    
    //find the vars having highest refcount so that at least one best LP rectangle can be eliminated
    public List<VariableCoefficientTuple> getBestChoiceBranchingVariables (   ){  
        
        List<VariableCoefficientTuple> recommendation = new ArrayList<VariableCoefficientTuple>();
       
        List<Rectangle> rectanglesToConsiderForBranchingVarCalculation =  this.getRectanglesToConsiderForBranchingVarCalculation( );
        
        //collect refcounts into this map
        Map<String, Integer> varRefCountMap = new HashMap<String, Integer> () ;
        
        List<VariableCoefficientTuple> variablesUsedForBranchingInThisRectangle =null;
        for (Rectangle rect: rectanglesToConsiderForBranchingVarCalculation){
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
        
        //dump refcount map
       
        /*for (Map.Entry<Integer, List<String> > entry : orderedVarRefCountMap.entrySet()){
            for (String str : entry.getValue()){
                logger.debug (str + " refcount "+ entry.getKey()) ;
            }
        }*/
        
        
        //get a list of these high refcount vars, so that at least 1 rectangle can be eliminated
        //also we must record whether var has to be 0 or 1 to eliminate this rect
        List<VariableCoefficientTuple> finalRecommendation = null;
        while (true ){
            finalRecommendation = isAtLeastOneRectangleEliminated(recommendation,   rectanglesToConsiderForBranchingVarCalculation);
            if (finalRecommendation!=null) break;
            
            int maxRefCountRemaining = Collections.min( orderedVarRefCountMap.keySet()); //recall ordered refcounts stored as -ve numbers
            //System.err.println("branching var refcount "+ maxRefCountRemaining);
            List<String> maxRefCountVars = orderedVarRefCountMap.remove( maxRefCountRemaining);
            for (String str :maxRefCountVars) {
                recommendation.add( new VariableCoefficientTuple(str, ZERO)); //0 or 1 will be populated when checking against removable rects.
            }
        }
        recommendation=finalRecommendation;
        
         logger.debug( "dump finalRecommendation ");
        for (VariableCoefficientTuple tuple: finalRecommendation) {
            logger.debug(tuple);
        }
        
        /*
        //pick the highest refcount var , in case of tie best objective coeff wins
        if (varRefCountMap.size()>ZERO){
            highestRefCount= Collections.max(varRefCountMap.values());
        } else {
            System.err.println("trying to branch a node which should have been marked feasible");
            exit(ONE);
        }
        List<String> candidateVars = new ArrayList<String> ();
        for (Map.Entry<String, Integer> entry : varRefCountMap.entrySet()){
            String thisVar = entry.getKey();
            Integer thisCount = entry.getValue();
            if (thisCount==highestRefCount) {
                candidateVars.add(thisVar) ;
                System.err.println(" var " + thisVar + " has value " +thisCount);
            }
            
        }
        if (candidateVars.size()==ONE) {
            bestVar=candidateVars.get(ZERO);
        }else if (candidateVars.size()>ONE) {
            bestVar =getVarWithHighestObjectiveCoeffMagnitude(candidateVars) ;
        } else {
            System.err.println("unable to find branching variable, error");
            exit(ONE);
        }
               
        return new VariableCoefficientTuple (bestVar, highestRefCount) ;*/
        
        return recommendation;
    }
    
    //if a rect can be elimiated by these vars, populate their 0 or 1 fixings and return true
    //else return false
    private List<VariableCoefficientTuple> isAtLeastOneRectangleEliminated ( List<VariableCoefficientTuple> recommendation, List<Rectangle> rectanglesToConsiderForBranchingVarCalculation) {
        //find a rect whose vars, minus the brancging vars for this node, are a subset of the input
        //if no such rect, return false, else return true and populate the 0 or 1 fixings for each var
        //for vars that need not be fixed, just remove them from the list
        
        List<VariableCoefficientTuple> result = null;
        
        for (Rectangle rect : rectanglesToConsiderForBranchingVarCalculation){
            List<VariableCoefficientTuple> variablesUsedForBranchingInThisRectangle = getVariablesUsedForBranchingInThisRectangle (rect);
            List<VariableCoefficientTuple> subset = isSubset (  recommendation , variablesUsedForBranchingInThisRectangle);
            if (null !=subset) {
                //we have found a rectangle tha can be eliminated
                result = subset ;
                break;
            }
        }
        
        return result;
    }
    
    //are branching vars included in recommendation
    private List<VariableCoefficientTuple> isSubset ( List<VariableCoefficientTuple> recommendation ,  List<VariableCoefficientTuple> branchingVars) {
        List<VariableCoefficientTuple> result = new ArrayList<VariableCoefficientTuple>() ;
        for (VariableCoefficientTuple branchingVar: branchingVars) {
            VariableCoefficientTuple fixing = getVarFixingForRecommendation(  branchingVar,  recommendation);
            if (null == fixing) {
                //at least 1 var is not in recommendation 
                result = null;
                break;
            }else {
                result.add(fixing );
            }
        }
        
        return result;
    }
    
    //if this branching var is in th elist of recommended vars, then return it along with its var fixing
    //else return null
    private VariableCoefficientTuple getVarFixingForRecommendation(VariableCoefficientTuple branchingVar,  List<VariableCoefficientTuple> recommendation) {
         VariableCoefficientTuple result = null;
         
         for ( VariableCoefficientTuple recommendedVar:  recommendation) {
             if (recommendedVar.varName.equals(branchingVar.varName)) {
                 result = new  VariableCoefficientTuple (branchingVar.varName , branchingVar.coeff) ;
                 break;    
             }             
         }
         
         return result;
    } 
    
    //get the best LP rectangles
    private List<Rectangle> getRectanglesToConsiderForBranchingVarCalculation () {
        
        List<Rectangle> rectanglesToConsider = new ArrayList<Rectangle> ();
         
        //for every constraint, see if it has rects at the best lp
        
        for (Map <Double, List<Rectangle>> rectMap: myInfeasibleRectanglesList) {
            List<Rectangle> rects = rectMap.get(this.bestLPValueAmongInfeasibleRects);
            if (null!=rects) {
                rectanglesToConsider.addAll(rects );
            }
        }
 
        return rectanglesToConsider;
    }

    
    //get vars used , consider only free vars
    private  List<VariableCoefficientTuple> getVariablesUsedForBranchingInThisRectangle(Rectangle rect){
        List<VariableCoefficientTuple> variablesUsedForBranchingInThisRectangle = new ArrayList<VariableCoefficientTuple> ();
        
        for (String var : rect.zeroFixedVariables) {
            if (!treeNode_zeroFixedVariables.contains(var) && !treeNode_oneFixedVariables.contains(var)) 
                variablesUsedForBranchingInThisRectangle.add(new VariableCoefficientTuple(var, ZERO) );
        }
        for (String var : rect.oneFixedVariables){
            //if (!variablesUsedForBranchingInThisRectangle.contains(var)) { //always true
                 if (!treeNode_zeroFixedVariables.contains(var) && !treeNode_oneFixedVariables.contains(var)) {
                     variablesUsedForBranchingInThisRectangle.add(new VariableCoefficientTuple(var, ONE));
                 }
            //}
        }
         
        return variablesUsedForBranchingInThisRectangle;
    }
    
    private void updateVariableRefCounts(Map<String, Integer>  varRefCountMap, List<VariableCoefficientTuple>  variablesUsedForBranchingInThisRectangle){
        for (VariableCoefficientTuple tuple : variablesUsedForBranchingInThisRectangle) {
            int currentCount = varRefCountMap.get(tuple.varName) == null ? ZERO: varRefCountMap.get(tuple.varName );
            varRefCountMap.put(tuple.varName, ONE+currentCount);
        }
    }
    
    /*private String getVarWithHighestObjectiveCoeffMagnitude(List<String> candidateVars){
        String bestVar = null;
        Double bestValSoFar = DOUBLE_ZERO;
         
        for (String  var: candidateVars){
            double thisVal = BNBR_Driver.objective.getObjectiveCoeffMagnitude(var);
            if ( Math.abs( thisVal) > Math.abs(bestValSoFar)) {
                bestValSoFar=thisVal;
                bestVar= var;
            }
        }
        
        
        return bestVar;
    }*/
    
    
 
    
}
