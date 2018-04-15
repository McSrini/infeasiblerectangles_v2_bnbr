/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ca.mcmaster.infeasiblerectangles_v2_bnbr.common.utils;

import ca.mcmaster.infeasiblerectangles_v2_bnbr.BNBR_Driver;
import static ca.mcmaster.infeasiblerectangles_v2_bnbr.Constants.LOG_FILE_EXTENSION;
import static ca.mcmaster.infeasiblerectangles_v2_bnbr.Constants.LOG_FOLDER;
import static ca.mcmaster.infeasiblerectangles_v2_bnbr.Constants.ONE;
import static ca.mcmaster.infeasiblerectangles_v2_bnbr.Constants.ZERO;
import static ca.mcmaster.infeasiblerectangles_v2_bnbr.Parameters.FRACTION_OF_INFERIOR_LP_RECTS_TO_CONSIDER;
import ca.mcmaster.infeasiblerectangles_v2_bnbr.common.Rectangle;
import ca.mcmaster.infeasiblerectangles_v2_bnbr.common.SolutionTree_Node;
import ca.mcmaster.infeasiblerectangles_v2_bnbr.common.TwoIntegerTuple;
import ca.mcmaster.infeasiblerectangles_v2_bnbr.common.VariableCoefficientTuple;
import static java.lang.System.exit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;
import org.apache.log4j.RollingFileAppender;

/**
 *
 * @author tamvadss
 * 
 * consider all best LP rects , plus upto 10% of the rest
 * 
 *  finds # of rects on each side for a var choice, chooses var with minimum max, or in case of tie min min
 * 
 * may need slight change to place where we branch and create the child node ? do we ??
 * 
 */
public class HalferBranchVarSuggestor {
    
    private SolutionTree_Node leafAboutToBranch ;
    
    private static Logger logger=Logger.getLogger(HalferBranchVarSuggestor.class);    
    static {
        logger.setLevel(Level.DEBUG);
        PatternLayout layout = new PatternLayout("%5p  %d  %F  %L  %m%n");     
        try {
            logger.addAppender(new  RollingFileAppender(layout,LOG_FOLDER+HalferBranchVarSuggestor.class.getSimpleName()+ LOG_FILE_EXTENSION));
            logger.setAdditivity(false);
        } catch (Exception ex) {
            ///
            System.err.println("Exit: unable to initialize logging");       
            exit(1);
        }
        
        
    }
    
    public HalferBranchVarSuggestor (SolutionTree_Node treeNode){
        this.leafAboutToBranch = treeNode;
    }
    
    //suggest a branching var
    public    List<VariableCoefficientTuple> suggest (   ){  
        List<VariableCoefficientTuple> recommendation = new ArrayList<VariableCoefficientTuple>();
       
        List<Rectangle> rectanglesToConsiderForBranchingVarCalculation =  this.getRectanglesToConsiderForBranchingVarCalculation( );
        
        //using these rects, find a var that split sthem in half, that is
        //find # of rects on 0 size and 1 side
        //pick var where the max of the two is smallest
        //in case of tie pick var where min is smallest
        String branchingVarSuggestion;
        int minMax = Integer.MAX_VALUE;
        for (Rectangle rect: rectanglesToConsiderForBranchingVarCalculation){
            
        }
        
      /*  
        not rite , ho wto do this quickly ?
        for  all vars in every rect, if var not in zero fixings, then its 1 compatible. If var not in 1 fixings then its 0 compatible
                finally selec th evar with smallest of 0 and 1 compatiility
        */
      
      return null;
    }
    
    
    private TwoIntegerTuple getVarRefCounts (String branchingVar, List<Rectangle> rectanglesToConsiderForBranchingVarCalculation ) {
        //for evrey rect, see how many have this var in thier zero fixing, and in their 1 fixing
        TwoIntegerTuple result = new TwoIntegerTuple ();
        for (Rectangle rect : rectanglesToConsiderForBranchingVarCalculation){
            if (rect.zeroFixedVariables.contains( branchingVar)) {
                result.oneSideCount++;
            }
            if (rect.oneFixedVariables.contains( branchingVar)){
                result.zeroSideCount++;
            }
        }
                
        return result;
    }
    
  
    
    //get vars used , consider only free vars
    private  List<String> getFreeVarsInThisRectangle(Rectangle rect){
        List<String> freeVars = new ArrayList<String> ();
        
        for (String var : rect.zeroFixedVariables) {
            if (!this.leafAboutToBranch.zeroFixedVariables.contains(var) && !this.leafAboutToBranch.oneFixedVariables.contains(var)) 
                freeVars.add( var  );
        }
        for (String var : rect.oneFixedVariables){
             
             if (!this.leafAboutToBranch.zeroFixedVariables.contains(var) && !this.leafAboutToBranch.oneFixedVariables.contains(var)) {
                 freeVars.add(var);
             }

        }
         
        return freeVars;
    }
    
    //get the best LP rectangles
    private List<Rectangle> getRectanglesToConsiderForBranchingVarCalculation () {
        
        List<Rectangle> rectanglesToConsider = new ArrayList<Rectangle> ();
         
        for (Map <Double, List<Rectangle>> rectMap: this.leafAboutToBranch.myInfeasibleRectanglesList) {
             
            for (    Map.Entry <Double, List<Rectangle>> entry : rectMap.entrySet()) {
                double thisLp = entry.getKey();
                double nodeLp =this.leafAboutToBranch.lpRelaxValueMinimization;
                double limit = ZERO;
                if (nodeLp>ZERO) {
                    limit = nodeLp * (ONE+FRACTION_OF_INFERIOR_LP_RECTS_TO_CONSIDER) ;
                }else if (nodeLp<ZERO){
                    limit = nodeLp * (ONE-FRACTION_OF_INFERIOR_LP_RECTS_TO_CONSIDER) ;
                }else {
                    limit = nodeLp;
                }
                boolean isWithinRange = thisLp <=limit;
                         
                         
                if (isWithinRange ) {
                    rectanglesToConsider.addAll(entry.getValue());
                } 
                 
            }
             
        }
 
        return rectanglesToConsider;
    }
    
}
