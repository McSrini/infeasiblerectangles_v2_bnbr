/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ca.mcmaster.infeasiblerectangles_v2_bnbr.common;

import static ca.mcmaster.infeasiblerectangles_v2_bnbr.Constants.ONE;

/**
 *
 * @author tamvadss
 */
public class VariableCoefficientTuple  implements Comparable{
    
    public String varName ;
    public Double coeff;
    
    public VariableCoefficientTuple (String varname, double coefficient)   {
    
        this.varName  =  varname; 
        coeff =coefficient;
    }
       
    //lowest magnitude coeff first
    public int compareTo(Object other) {
       return Math.abs(this.coeff )> Math.abs(  ((VariableCoefficientTuple)other).coeff )? ONE:-ONE;
    } 
    
    public String toString (){
        return "Tuple is " + this.varName + " " + this.coeff;
    }
}
