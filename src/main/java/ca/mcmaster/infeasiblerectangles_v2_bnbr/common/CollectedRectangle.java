/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ca.mcmaster.infeasiblerectangles_v2_bnbr.common;

import java.util.List;

/**
 *
 * @author tamvadss
 */
public class CollectedRectangle extends Rectangle{
     
    //no need for this class right now, can simply use base class
    public CollectedRectangle(List<String> zeroFixedVariables, List<String> oneFixedVariables ) {
        super(zeroFixedVariables, oneFixedVariables);
    }
    
}
