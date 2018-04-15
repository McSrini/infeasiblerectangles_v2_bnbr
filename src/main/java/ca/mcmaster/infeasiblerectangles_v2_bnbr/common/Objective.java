/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ca.mcmaster.infeasiblerectangles_v2_bnbr.common;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 *
 * @author tamvadss
 */
public class Objective {
        
    public List<VariableCoefficientTuple>   objectiveExpr ; 
    
    private Map<String, Double> coefficientMagnitudes = new HashMap <String, Double> ();
    private Map<String, Double> coefficientsWithSign = new HashMap <String, Double> ();
      
    public Objective( List<VariableCoefficientTuple>  expr   ) {
         
         
        objectiveExpr = expr;
        for (VariableCoefficientTuple tuple : this.objectiveExpr){
            this.coefficientMagnitudes.put(tuple.varName, Math.abs(tuple.coeff));
            this.coefficientsWithSign.put(tuple.varName,  tuple.coeff);
        }
        
    }
    
    public Double getObjectiveCoeff( String  var ){
         
        return coefficientsWithSign.get(var);
    }
    
    
    
    public Double getObjectiveCoeffMagnitude( String  var ){
         
        return coefficientMagnitudes.get(var);
    }
    
    public String toString() {
        String str = "";
        
        for (VariableCoefficientTuple tuple : objectiveExpr) {
            str += ("Var is " + tuple.varName  + " and its coeff is "+ tuple.coeff+"\n") ;
        }
        return str;
    }
}
