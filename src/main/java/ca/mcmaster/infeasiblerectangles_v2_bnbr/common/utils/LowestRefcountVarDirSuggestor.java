/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ca.mcmaster.infeasiblerectangles_v2_bnbr.common.utils;

import ca.mcmaster.infeasiblerectangles_v2_bnbr.BNBR_Driver;
import static ca.mcmaster.infeasiblerectangles_v2_bnbr.Constants.DOUBLE_ONE;
import static ca.mcmaster.infeasiblerectangles_v2_bnbr.Constants.DOUBLE_ZERO;
import static ca.mcmaster.infeasiblerectangles_v2_bnbr.Constants.LOG_FILE_EXTENSION;
import static ca.mcmaster.infeasiblerectangles_v2_bnbr.Constants.LOG_FOLDER;
import static ca.mcmaster.infeasiblerectangles_v2_bnbr.Constants.ONE;
import static ca.mcmaster.infeasiblerectangles_v2_bnbr.Constants.ZERO;
import ca.mcmaster.infeasiblerectangles_v2_bnbr.common.Rectangle;
import ca.mcmaster.infeasiblerectangles_v2_bnbr.common.SolutionTree_Node;
import ca.mcmaster.infeasiblerectangles_v2_bnbr.common.VariableCoefficientTuple;
import static java.lang.System.exit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;
import org.apache.log4j.RollingFileAppender;

/**
 *
 * @author tamvadss
 * 
 * suggests branching var and direction which occurs lowest times in the best lp rects 
 * 
 */
public class LowestRefcountVarDirSuggestor {
        
    private  List<TreeMap <Double, List<Rectangle>> > myInfeasibleRectanglesList = null;  // per constraint
    private List<String> treeNode_zeroFixedVariables;
    private   List<String>  treeNode_oneFixedVariables;
    private double bestLPValueAmongInfeasibleRects = ZERO;
     
    
    private static Logger logger=Logger.getLogger(LowestRefcountVarDirSuggestor.class);    
    static {
        logger.setLevel(Level.DEBUG);
        PatternLayout layout = new PatternLayout("%5p  %d  %F  %L  %m%n");     
        try {
            logger.addAppender(new  RollingFileAppender(layout,LOG_FOLDER+LowestRefcountVarDirSuggestor.class.getSimpleName()+ LOG_FILE_EXTENSION));
            logger.setAdditivity(false);
        } catch (Exception ex) {
            ///
            System.err.println("Exit: unable to initialize logging");       
            exit(1);
        }
    }
    
    public LowestRefcountVarDirSuggestor ( SolutionTree_Node treeNode, double bestLPAmongAllInfeasibleRectangles) {
        this.myInfeasibleRectanglesList = treeNode.myInfeasibleRectanglesList;
        this.treeNode_oneFixedVariables=treeNode.oneFixedVariables;
        this.treeNode_zeroFixedVariables = treeNode.zeroFixedVariables;
        this.bestLPValueAmongInfeasibleRects =bestLPAmongAllInfeasibleRectangles;
    }
    
    //suggests branching var and direction which occurs lowest times in the best lp rects 
    //returns its refcount
    public int getBestChoiceBranchingVariable (  VariableCoefficientTuple  recommendation  ){  
         
       
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
        
        //find th evar and dir that occurs the lowest number of times
        int intLowestRefCountFound = Integer.MAX_VALUE;
        String nameOFLowestRefCountFoundVar = null;
        boolean  directionOfLowestRefCountFound_is_Zero = true;
        for (String var : BNBR_Driver.allVariablesInModel) {
            
            //ignore vars already included in branching conditions for selectedLeaf
            if (this.treeNode_oneFixedVariables.contains( var)|| this.treeNode_zeroFixedVariables.contains(var)){
                continue;
            }
            
            Integer thisZeroCount = zeroVar_RefCountMap.get(var);
            if (thisZeroCount==null) {
                 
                 nameOFLowestRefCountFoundVar = var;
                 break;
            }else if (thisZeroCount<intLowestRefCountFound) {
                intLowestRefCountFound=thisZeroCount;
                nameOFLowestRefCountFoundVar= var;
            }
            
            Integer thisOneCount = oneVar_RefCountMap.get(var);
            if (thisOneCount==null) {
                 directionOfLowestRefCountFound_is_Zero = false;
                 nameOFLowestRefCountFoundVar = var;
                 break;
            }else if (thisOneCount < intLowestRefCountFound) {
                intLowestRefCountFound= thisOneCount;
                nameOFLowestRefCountFoundVar= var;
                directionOfLowestRefCountFound_is_Zero=false;
            }
            
        }
         
        if (directionOfLowestRefCountFound_is_Zero){
            recommendation.varName=nameOFLowestRefCountFoundVar;
            recommendation.coeff=                    DOUBLE_ZERO;
          
        }else {
            
            recommendation.varName=nameOFLowestRefCountFoundVar;
            recommendation.coeff=                    DOUBLE_ONE;
            
             
        }
        
        logger.debug("recommendation is " + recommendation +" lowest refcount is " + intLowestRefCountFound);
        return intLowestRefCountFound;
    }
        
    private void updateVariableRefCounts(Map<String, Integer>  zeroVar_RefCountMap,  Map<String, Integer> oneVar_RefCountMap,
                                         List<VariableCoefficientTuple>  variablesUsedForBranchingInThisRectangle){
        for (VariableCoefficientTuple tuple : variablesUsedForBranchingInThisRectangle) {
            int zero_currentCount = zeroVar_RefCountMap.get(tuple.varName) == null ? ZERO: zeroVar_RefCountMap.get(tuple.varName );
            zeroVar_RefCountMap.put(tuple.varName, ONE+zero_currentCount);
            int one_currentCount = oneVar_RefCountMap.get(tuple.varName) == null ? ZERO: oneVar_RefCountMap.get(tuple.varName );
            oneVar_RefCountMap.put(tuple.varName, ONE+one_currentCount);
        }
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
}
