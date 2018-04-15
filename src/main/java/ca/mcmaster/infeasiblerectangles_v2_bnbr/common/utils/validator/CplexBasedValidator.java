/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ca.mcmaster.infeasiblerectangles_v2_bnbr.common.utils.validator;

import static ca.mcmaster.infeasiblerectangles_v2_bnbr.Constants.*;
import static ca.mcmaster.infeasiblerectangles_v2_bnbr.Parameters.MIP_FILENAME;
import ilog.concert.IloException;
import ilog.concert.IloLPMatrix;
import ilog.concert.IloNumVar;
import ilog.cplex.IloCplex;
import ilog.cplex.IloCplex.Status;
import java.util.*;
import java.util.Map;
import java.util.TreeMap;

/**
 *
 * @author tamvadss
 */
public class CplexBasedValidator {
    
    private IloCplex cplex;
    private double errorMagnitude = ZERO;
    
    public CplexBasedValidator (List<String> zeroFixedvars , double expectedSoln ) throws IloException{
        IloCplex cplex = new IloCplex () ;
        cplex.importModel( MIP_FILENAME);
         
        IloLPMatrix lpMatrix = (IloLPMatrix)cplex.LPMatrixIterator().next();
        IloNumVar[] variables  =lpMatrix.getNumVars();
        
        Map< String,Double > zeroFixMap = new TreeMap<String, Double> ();
        for (String str : zeroFixedvars){
            zeroFixMap.put(str,DOUBLE_ZERO);
        }
        
        Map< String,Double > lowerBounds = new TreeMap<String, Double> ();
        for (IloNumVar var :variables){
            if (! zeroFixMap.containsKey(var.getName())){
                //lowerBounds.put (var.getName(), 1.0);
                
                cplex.addLe(ONE , var );
                
            }
        }
        
        for (IloNumVar var :variables){
            if ( zeroFixMap.containsKey(var.getName())){
                //lowerBounds.put (var.getName(), 1.0);
                
                cplex.addGe(ZERO , var );
                
            }
        }
        
        //apply var bounds and solve
        merge (  variables ,  new TreeMap <String, Double> () , new TreeMap <String, Double> ()  ) ;
        //cplex.exportModel("F:\\temporary files here\\p6b_allfixed.lp");
        cplex.solve();
        errorMagnitude = cplex.getObjValue()-expectedSoln;
    }
    
    public boolean isValid () throws IloException {
         
        return this.errorMagnitude==ZERO;
    }
    
    public double getSolution () throws IloException {
        return cplex.getObjValue();
    }
       
    /**
     * 
     * To the CPLEX object ,  apply all the bounds mentioned in attachment
     */
    private   static void  merge (  IloNumVar[] variables ,  Map< String,Double > lowerBounds, Map< String,Double > upperBounds  ) throws IloException {


        for (int index = 0 ; index <variables.length; index ++ ){

            IloNumVar thisVar = variables[index];
            updateVariableBounds(thisVar,lowerBounds, false );
            updateVariableBounds(thisVar,upperBounds, true );

        }       
    }
    
    /**
     * 
     *  Update variable bounds as specified    
     */
    private    static  void updateVariableBounds(IloNumVar var, Map< String,Double > newBounds, boolean isUpperBound   ) 
            throws IloException{

        String varName = var.getName();
        boolean isPresentInNewBounds = newBounds.containsKey(varName);

        if (isPresentInNewBounds) {
            double newBound =   newBounds.get(varName)  ;
            if (isUpperBound){
                if ( var.getUB() > newBound ){
                    //update the more restrictive upper bound
                    var.setUB( newBound );
                    //logger.info(" var " + varName + " set upper bound " + newBound ) ;
                }
            }else{
                if ( var.getLB() < newBound){
                    //update the more restrictive lower bound
                    var.setLB(newBound);
                    //logger.info(" var " + varName + " set lower bound " + newBound ) ;
                }
            }               
        }

    }  
  
 
}
