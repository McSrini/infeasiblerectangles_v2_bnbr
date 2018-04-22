/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ca.mcmaster.infeasiblerectangles_v2_bnbr.solvers.cplex;

import java.util.* ;

/**
 *
 * @author tamvadss
 */
public class NodeAttachment {
    public List<String> zeroFixedVars = new ArrayList<String>();
    public List<String> oneFixedVars = new ArrayList<String>();
    public NodeAttachment (String branchingVar, boolean isZeroFixed, List<String> parentZeroFixedVars,List<String> parentOneFixedVars ){
        if (isZeroFixed){
            zeroFixedVars.add(branchingVar) ;
        } else {
            oneFixedVars.add(branchingVar);
        }
        zeroFixedVars.addAll(parentZeroFixedVars );
        oneFixedVars.addAll( parentOneFixedVars);
    }
    public NodeAttachment (){
         
    }
}
