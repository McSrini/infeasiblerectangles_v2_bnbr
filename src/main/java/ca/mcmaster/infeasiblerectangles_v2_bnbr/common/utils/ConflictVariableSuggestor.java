/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ca.mcmaster.infeasiblerectangles_v2_bnbr.common.utils;

import static ca.mcmaster.infeasiblerectangles_v2_bnbr.Constants.DOUBLE_ZERO;
import static ca.mcmaster.infeasiblerectangles_v2_bnbr.Constants.LOG_FILE_EXTENSION;
import static ca.mcmaster.infeasiblerectangles_v2_bnbr.Constants.LOG_FOLDER;
import static ca.mcmaster.infeasiblerectangles_v2_bnbr.Constants.ONE;
import static ca.mcmaster.infeasiblerectangles_v2_bnbr.Constants.ZERO;
import ca.mcmaster.infeasiblerectangles_v2_bnbr.common.Rectangle;
import ca.mcmaster.infeasiblerectangles_v2_bnbr.common.SolutionTree_Node;
import ca.mcmaster.infeasiblerectangles_v2_bnbr.common.TwoIntegerTuple;
import ca.mcmaster.infeasiblerectangles_v2_bnbr.common.VariableCoefficientTuple;
import static java.lang.System.exit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;
import org.apache.log4j.RollingFileAppender;

/**
 *
 * @author tamvadss
  
 * 
 */
public class ConflictVariableSuggestor {
    
    private  List<TreeMap <Double, List<Rectangle>> > myInfeasibleRectanglesList = null;  // per constraint
    private List<String> treeNode_zeroFixedVariables;
    private   List<String>  treeNode_oneFixedVariables;
    //private double bestLPValueAmongInfeasibleRects = ZERO;
      
    private static Logger logger=Logger.getLogger(ConflictVariableSuggestor.class);    
    static {
        logger.setLevel(Level.WARN);
        PatternLayout layout = new PatternLayout("%5p  %d  %F  %L  %m%n");     
        try {
            logger.addAppender(new  RollingFileAppender(layout,LOG_FOLDER+ConflictVariableSuggestor.class.getSimpleName()+ LOG_FILE_EXTENSION));
            logger.setAdditivity(false);
        } catch (Exception ex) {
            ///
            System.err.println("Exit: unable to initialize logging");       
            exit(1);
        }
    }
    
    public ConflictVariableSuggestor ( SolutionTree_Node treeNode /*, double bestLPAmongAllInfeasibleRectangles*/ ) {
        this.myInfeasibleRectanglesList = treeNode.myInfeasibleRectanglesList;
        this.treeNode_oneFixedVariables=treeNode.oneFixedVariables;
        this.treeNode_zeroFixedVariables = treeNode.zeroFixedVariables;
        //this.bestLPValueAmongInfeasibleRects =bestLPAmongAllInfeasibleRectangles; 
    }
     
    //return max refcount of any variable in this node , if ==1 can solve node by enumeration
    //childcounts is populated with rect counts of left and child branches, this can be used for node sequencing
    //
    //suggest var that will remove from one side  the max possible number of best rects
    public VariableCoefficientTuple suggestBranchingVariable (TwoIntegerTuple childRectCounts  ) {
        
        VariableCoefficientTuple result= new VariableCoefficientTuple(null, ZERO) ;
        
        //first get the best lp rects
        List<Rectangle> rectanglesToConsiderForBranchingVarCalculation =  this.getRectanglesToConsiderForBranchingVarCalculation( );
        
        //collect 0 direction refcounts into this map
        Map<String, Integer> zeroVar_RefCountMap = new HashMap<String, Integer> () ;
        //and one diirection refconts into this map
        Map<String, Integer> oneVar_RefCountMap = new HashMap<String, Integer> () ;
         
        List<VariableCoefficientTuple> variablesUsedForBranchingInThisRectangle =null;
        for (Rectangle rect: rectanglesToConsiderForBranchingVarCalculation){
            variablesUsedForBranchingInThisRectangle = getVariablesUsedForBranchingInThisRectangle (rect);
            if (variablesUsedForBranchingInThisRectangle.size()>ZERO) {
                this.updateVariableRefCounts(zeroVar_RefCountMap, oneVar_RefCountMap, variablesUsedForBranchingInThisRectangle) ;
            }else {
                System.err.print("trying to branch a node which should have been marked infeasible" );
                exit(ONE);
            }
        }
        
        
        //now find the highest refcount var, no matter its direction
        int highZeroFreq = zeroVar_RefCountMap.isEmpty()?ZERO:Collections.max( zeroVar_RefCountMap.values());
        int highOneFreq =  oneVar_RefCountMap.isEmpty()? ZERO: Collections.max(oneVar_RefCountMap.values()) ;
        
        //we need to find the selected var's refcount on the other side
        int selectedVarRefCountOnOtherSide = ZERO;
        
        int totalRects = getNumberOfRects(this.myInfeasibleRectanglesList) ;
        
        if (highZeroFreq>highOneFreq) {
            result.varName=getHighestFreqVar (zeroVar_RefCountMap,highZeroFreq ) ;
            result.coeff=highZeroFreq;
            selectedVarRefCountOnOtherSide= oneVar_RefCountMap.get( result.varName)==null? ZERO :  oneVar_RefCountMap.get( result.varName);
            //this is how many rects will survive on either side
            childRectCounts.zeroSideCount = totalRects - selectedVarRefCountOnOtherSide;
            childRectCounts.oneSideCount=totalRects -highZeroFreq ;
            
        } else {
            result.varName=getHighestFreqVar (oneVar_RefCountMap,highOneFreq ) ;
            result.coeff=highOneFreq;
            selectedVarRefCountOnOtherSide= zeroVar_RefCountMap.get( result.varName)==null? ZERO :  zeroVar_RefCountMap.get( result.varName);
            
            childRectCounts.zeroSideCount = totalRects - highOneFreq;
            childRectCounts.oneSideCount=totalRects - selectedVarRefCountOnOtherSide;
        }
        
        return  result;
    }
    
    private String getHighestFreqVar ( Map<String, Integer> refCountMap,int freq ) {
        String result = null;
        for (Entry<String , Integer> entry : refCountMap.entrySet()){
            if (entry.getValue()==freq){
                result = entry.getKey();
            }
        }
        return result;
    }
        
    private static int getNumberOfRects ( List<TreeMap<Double, List<Rectangle> >> mapList){
        int count = ZERO;
        for (Map<Double, List<Rectangle> > map2 : mapList){
            count +=getNumberOfRects(map2) ;
        }
        return count;
    }
    private static int getNumberOfRects ( Map<Double, List<Rectangle> > map2){
        int count = ZERO;
        for (List<Rectangle> rectlist : map2.values()){
            count +=rectlist.size();
        }
        return count;
    }
        
    private void updateVariableRefCounts(Map<String, Integer>  zeroVar_RefCountMap,  Map<String, Integer> oneVar_RefCountMap,
                                         List<VariableCoefficientTuple>  variablesUsedForBranchingInThisRectangle){
        for (VariableCoefficientTuple tuple : variablesUsedForBranchingInThisRectangle) {
            if (tuple.coeff==ZERO){
                int zero_currentCount = zeroVar_RefCountMap.get(tuple.varName) == null ? ZERO: zeroVar_RefCountMap.get(tuple.varName );
                zeroVar_RefCountMap.put(tuple.varName, ONE+zero_currentCount);
            }else {
                int one_currentCount = oneVar_RefCountMap.get(tuple.varName) == null ? ZERO: oneVar_RefCountMap.get(tuple.varName );
                oneVar_RefCountMap.put(tuple.varName, ONE+one_currentCount);
            }           
        }
    }
    
    //get vars used , consider only free vars
    private  List<VariableCoefficientTuple> getVariablesUsedForBranchingInThisRectangle(Rectangle rect){
        List<VariableCoefficientTuple> variablesUsedForBranchingInThisRectangle = new ArrayList<VariableCoefficientTuple> ();
        
        for (String var : rect.zeroFixedVariables) {
            if (!treeNode_zeroFixedVariables.contains(var)  ) 
                variablesUsedForBranchingInThisRectangle.add(new VariableCoefficientTuple(var, ZERO) );
        }
        for (String var : rect.oneFixedVariables){         
            if (  !treeNode_oneFixedVariables.contains(var)) {
                variablesUsedForBranchingInThisRectangle.add(new VariableCoefficientTuple(var, ONE));
            }  
        }
         
        return variablesUsedForBranchingInThisRectangle;
    }
    
    //get the best LP rectangles
    private List<Rectangle> getRectanglesToConsiderForBranchingVarCalculation () {
        
        List<Rectangle> rectanglesToConsider = new ArrayList<Rectangle> ();
         
        //for every constraint, see if it has rects at the best lp
        
        for (Map <Double, List<Rectangle>> rectMap: myInfeasibleRectanglesList) {
            for (List<Rectangle> rectList : rectMap.values()) { 
                
                    rectanglesToConsider.addAll(rectList );
                         
            } 
        }
 
        return rectanglesToConsider;
    }
    
}
