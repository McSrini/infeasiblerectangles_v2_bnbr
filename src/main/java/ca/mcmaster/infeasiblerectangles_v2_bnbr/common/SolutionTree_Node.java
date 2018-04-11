/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ca.mcmaster.infeasiblerectangles_v2_bnbr.common;

import static ca.mcmaster.infeasiblerectangles_v2_bnbr.Constants.*;
import java.util.*;
import java.util.Map;

/**
 *
 * @author tamvadss
 */
public class SolutionTree_Node extends Rectangle{
    
    public static long NODE_ID = ZERO;
    
    public long myId =-ONE;
    
    
    //list of infeasible rectangles that are compatible with this rectangle
    //key is lp relax value
    public Map <Double, List<CollectedRectangle>>  myInfeasibleRectangles = null; 
    
    public SolutionTree_Node (List <String> zeroFixedVariables , List <String> oneFixedVariables , Map <Double, List<CollectedRectangle>>  myCompatibleRectangles){
        super( zeroFixedVariables ,  oneFixedVariables) ;
        this.myInfeasibleRectangles = myCompatibleRectangles;
        myId =NODE_ID++;
    }
    
    public SolutionTree_Node(){
        super( new ArrayList <String>() , new ArrayList <String>()) ;
         myId =NODE_ID++;
    }
    public String toString (){
        return "Node id " + myId + " " + super.toString() + " infeas map size " + myInfeasibleRectangles.size();
    }

    
}
