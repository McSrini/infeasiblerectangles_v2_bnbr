/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ca.mcmaster.infeasiblerectangles_v2_bnbr.common;

import static ca.mcmaster.infeasiblerectangles_v2_bnbr.Constants.ZERO;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 *
 * @author tamvadss
 */
public class LowerBoundConstraint {
        
    public List<VariableCoefficientTuple>   sortedConstraintExpr ;     
    public double lowerBound; 
    public String name;    
      
    //private constructor for local use in this file
    private   LowerBoundConstraint(  ){
        
    }
    
    public LowerBoundConstraint( String name, List<VariableCoefficientTuple>   constraint_Expr ,    double lowerBound ) {
        this.lowerBound =lowerBound;
        Collections.sort(constraint_Expr ); //lowest magnitude coeff first
        sortedConstraintExpr =constraint_Expr ;
        this.name= name;
        
    }
    
    //even with the worst possible choice of free vars, am I still feasible ?
    public boolean isGauranteedFeasible (boolean isStrict){
        double worstValue = ZERO ;
        for (VariableCoefficientTuple tuple : sortedConstraintExpr) {
            if (tuple.coeff<ZERO)    worstValue +=tuple.coeff;
        }
        return isStrict? (worstValue > this.lowerBound) : (worstValue >= this.lowerBound);
    }
    
    public String toString() {
        String str = "Constraint name "+ name ;
        str+=("Lower bound is "+ this.lowerBound+"\n");
        for (VariableCoefficientTuple tuple : sortedConstraintExpr) {
            str += ("Var is " + tuple.varName  + " and its coeff is "+ tuple.coeff+"\n") ;
        }
        return str;
    }
    
    
    //constraint  , disregarding fixed vars    
    public LowerBoundConstraint  getReducedConstraint (  List <String> varsFixedAtZero, List <String> varsFixedAtOne){
        
        LowerBoundConstraint reducedConstraint = new LowerBoundConstraint();
        
        reducedConstraint.sortedConstraintExpr = new ArrayList<VariableCoefficientTuple> ();
        reducedConstraint.lowerBound = this.lowerBound;
        reducedConstraint.name = this.name;
        
        List <String> fixedVarNamesZero = new ArrayList <String>();
        List <String> fixedVarNamesOne = new ArrayList <String>();
        for (String binvar : varsFixedAtOne){
            fixedVarNamesOne.add(binvar );
        }
        for (String binvar : varsFixedAtZero){
            fixedVarNamesZero.add(binvar );
        }
                
        for (VariableCoefficientTuple tuple : this.sortedConstraintExpr){
            if (!fixedVarNamesOne.contains(tuple.varName) && !fixedVarNamesZero.contains(tuple.varName)  ) {
                reducedConstraint.sortedConstraintExpr .add(tuple);
            }else {
                //find the val to which this var is fixed                
                if (fixedVarNamesOne.contains(tuple.varName)) reducedConstraint.lowerBound-= tuple.coeff;
            }
        }
        return reducedConstraint;
    }
    
}
