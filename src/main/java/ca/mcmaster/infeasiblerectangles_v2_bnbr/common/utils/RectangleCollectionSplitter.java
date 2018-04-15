/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ca.mcmaster.infeasiblerectangles_v2_bnbr.common.utils;

import static ca.mcmaster.infeasiblerectangles_v2_bnbr.Constants.ZERO;
import ca.mcmaster.infeasiblerectangles_v2_bnbr.common.Rectangle;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 *
 * @author tamvadss
 */
public class RectangleCollectionSplitter {
     
    //map to be split
    private TreeMap<Double, List<Rectangle>  > input_Rectangle_Set=null;
    
    public TreeMap<Double, List<Rectangle>  > rectangle_Compatible_With_Zero_Side = new TreeMap <Double, List<Rectangle>>  ();    
    public TreeMap<Double, List<Rectangle>  > rectangle_Compatible_With_One_Side = new TreeMap <Double, List<Rectangle>>  ();    
    
    public boolean isZeroSideInfeasible = false;
    public boolean isOneSideInfeasible = false;
    
    public    RectangleCollectionSplitter (TreeMap<Double, List<Rectangle>  > rectangles_to_be_Split,
                                           boolean isZeroChildFeasible , boolean isOneChildFeasible){
        this.input_Rectangle_Set =rectangles_to_be_Split;
        this.isZeroSideInfeasible = ! isZeroChildFeasible;
        this.isOneSideInfeasible = ! isOneChildFeasible;
    }
    
    public void split (  String branchingVar ,  List <String> nodeZeroFixedVariables, List <String> nodeOneFixedVariables) {
        
        //by this branching, is one of the sides   matching an infeasible rect ?
        //if so , there will be no split assigned to that side
        
       
        
        for (Map.Entry<Double, List<Rectangle>>   entry : this.input_Rectangle_Set.entrySet()) {
            List<Rectangle> zeroRects = new ArrayList<Rectangle>();
            List<Rectangle> oneRects = new ArrayList<Rectangle>();
            for (Rectangle rect : entry.getValue()) {
                if (! rect.oneFixedVariables.contains(branchingVar) && !isZeroSideInfeasible)  {
                    boolean isSubset = isRectSubSetOFBranchingConsditions(true, branchingVar  ,   rect,
                                                        nodeZeroFixedVariables,   nodeOneFixedVariables) ;
                    if (isSubset ==false) {
                        zeroRects.add(rect);
                    } else  isZeroSideInfeasible=true;
                    /* if (isSubset.equals(SubSetEnum.PERFECT_MATCH)) {
                        isZeroSideInfeasible=true;
                    }*/
                    
                }
                if (! rect.zeroFixedVariables.contains(branchingVar) && !isOneSideInfeasible) {
                    boolean isSubset = isRectSubSetOFBranchingConsditions(false, branchingVar  ,   rect,
                                                        nodeZeroFixedVariables,   nodeOneFixedVariables) ;
                    if (isSubset==false) {
                        oneRects.add(rect);
                    }else  isOneSideInfeasible=true;
                    /*if (isSubset.equals(SubSetEnum.PERFECT_MATCH)){
                        isOneSideInfeasible=true;
                    }  */                   
                }                 
            }
            if (zeroRects.size()>ZERO && !isZeroSideInfeasible) rectangle_Compatible_With_Zero_Side.put( entry.getKey(), zeroRects );
            if (oneRects.size()>ZERO && !isOneSideInfeasible) rectangle_Compatible_With_One_Side.put (entry.getKey(), oneRects);
        }  
        
        if (isZeroSideInfeasible) rectangle_Compatible_With_Zero_Side.clear();
        if (isOneSideInfeasible)  rectangle_Compatible_With_One_Side.clear();
            
    }
    
      
    private boolean isRectSubSetOFBranchingConsditions(boolean isZeroSide, String branching_var, Rectangle rect,
                                                      List <String> nodeZeroFixedVariables, List <String> nodeOneFixedVariables) {
        boolean result = true;
        
       
        
        List <String> nodeZeroFixedVariablesAugmented = new ArrayList <String> ();
        nodeZeroFixedVariablesAugmented.addAll(nodeZeroFixedVariables);
        List <String> nodeOneFixedVariablesAugmented = new ArrayList <String> ();
        nodeOneFixedVariablesAugmented.addAll(nodeOneFixedVariables);
        if (isZeroSide) {
            nodeZeroFixedVariablesAugmented.add(branching_var) ;
        }else {
            nodeOneFixedVariablesAugmented.add(branching_var);
        }
        
        //are all zero fixings for the node , plus this var if dir is down, a superset of rects's one fixings ?
        for (String var : rect.zeroFixedVariables) {
            if (!nodeZeroFixedVariablesAugmented.contains(var)  ) {
                result = false;
                break;
            }
        }
        
        //are all one fixings for the node , plus this var if dir is up, a superset of rects's one fixings?
        for (String var : rect.oneFixedVariables) {
            if (false==result) break;
            if (!nodeOneFixedVariablesAugmented.contains(var)  ) {
                result = false; 
                break;
            }
        }
       
        
        return result;        
    }
 
    
}
