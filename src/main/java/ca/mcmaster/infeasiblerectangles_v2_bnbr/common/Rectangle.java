/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ca.mcmaster.infeasiblerectangles_v2_bnbr.common;

import ca.mcmaster.infeasiblerectangles_v2_bnbr.BNBR_Driver;
import static ca.mcmaster.infeasiblerectangles_v2_bnbr.Constants.*;
import static java.lang.System.exit;
import java.util.ArrayList;
import java.util.List; 
 

/**
 *
 * @author tamvadss
 */
public   class Rectangle {
    
    //note that some vars can be free
    public List <String> zeroFixedVariables = new ArrayList <String>();
    public List <String> oneFixedVariables = new ArrayList <String>();
    
    public double lpRelaxValueMinimization;     
    public List <String> lpVertex_zeroFixedVariables = new ArrayList <String>();
    public List <String> lpVertex_oneFixedVariables  = new ArrayList <String>();
    
    public double lpRelaxValue_Max_imization;     
    public List <String> max_lpVertex_zeroFixedVariables = new ArrayList <String>();
    public List <String> max_lpVertex_oneFixedVariables  = new ArrayList <String>();
         
    //private static Logger logger=Logger.getLogger(Rectangle.class);
        
    /*static {
                    
        logger=Logger.getLogger(Rectangle.class);
        logger.setLevel(Level.DEBUG);
        
        PatternLayout layout = new PatternLayout("%5p  %d  %F  %L  %m%n");     
        try {
            RollingFileAppender rfa = new  RollingFileAppender(layout,LOG_FOLDER+Rectangle.class.getSimpleName()+ LOG_FILE_EXTENSION);
           
            rfa.setMaxBackupIndex(TEN*TEN);
            logger.addAppender(rfa);
            logger.setAdditivity(false);
            
        } catch (Exception ex) {
            System.err.println("Exit: unable to initialize logging");        
            exit(1);
        }
    }*/
    
    public Rectangle (List <String> zeroFixedVariables , List <String> oneFixedVariables ){
        this.zeroFixedVariables .addAll(zeroFixedVariables);
        this.oneFixedVariables  .addAll( oneFixedVariables);
      
    }
    
    public int getDepth () {
        return this.zeroFixedVariables.size()+this.oneFixedVariables.size();
    }
    
    //get min possible value
    public double getLpRelaxVertex_Max_imization () {
        
        this.lpRelaxValue_Max_imization = ZERO;
        this.max_lpVertex_oneFixedVariables.clear();
        this.max_lpVertex_zeroFixedVariables.clear();
        
        for (VariableCoefficientTuple tuple: BNBR_Driver.objective.objectiveExpr){
            if (this.oneFixedVariables.contains(tuple.varName) ){
                this. lpRelaxValue_Max_imization+=tuple.coeff;
                max_lpVertex_oneFixedVariables.add(tuple.varName) ;
            }else if (this.zeroFixedVariables.contains(tuple.varName)) {
                max_lpVertex_zeroFixedVariables.add(tuple.varName);
            }
            
            if (!this.oneFixedVariables.contains(tuple.varName) && !this.zeroFixedVariables.contains(tuple.varName)   ){
                //free var
                if (tuple.coeff>ZERO) {
                    this.lpRelaxValue_Max_imization+=tuple.coeff;
                    max_lpVertex_oneFixedVariables.add(tuple.varName) ;
                }else {
                    max_lpVertex_zeroFixedVariables.add(tuple.varName);
                }
            }
        }
        
        return lpRelaxValue_Max_imization;
    }
    
    
    //get min possible value
    public double getLpRelaxVertex_Minimization () {
        
        this.lpRelaxValueMinimization = ZERO;
        this.lpVertex_oneFixedVariables.clear();
        this.lpVertex_zeroFixedVariables.clear();
        
        for (VariableCoefficientTuple tuple: BNBR_Driver.objective.objectiveExpr){
            if (this.oneFixedVariables.contains(tuple.varName) ){
                this.lpRelaxValueMinimization+=tuple.coeff;
                lpVertex_oneFixedVariables.add(tuple.varName) ;
            }else if (this.zeroFixedVariables.contains(tuple.varName)) {
                lpVertex_zeroFixedVariables.add(tuple.varName);
            }
            
            if (!this.oneFixedVariables.contains(tuple.varName) && !this.zeroFixedVariables.contains(tuple.varName)   ){
                //free var
                if (tuple.coeff<ZERO) {
                    this.lpRelaxValueMinimization+=tuple.coeff;
                    lpVertex_oneFixedVariables.add(tuple.varName) ;
                }else {
                    lpVertex_zeroFixedVariables.add(tuple.varName);
                }
            }
        }
        
        return lpRelaxValueMinimization;
    }
    
    public String toString (){
        String result=" lp realx " + this.lpRelaxValueMinimization;
        result += " --- Zero fixed vars :";
        for (String str: zeroFixedVariables){
            result += str + ",";
        }
        result += "  -- One fixed vars :";
        for (String str: oneFixedVariables){
            result += str + ",";
        }
        return result;
    }
    
    public   List<String> getVariablesUsedForBranchingInThisRectangle( ){
        List<String> variablesUsedForBranchingInThisRectangle = new ArrayList<String> ();
        
        for (String var : this.zeroFixedVariables) {
            variablesUsedForBranchingInThisRectangle.add( var  );
        }
        for (String var : this.oneFixedVariables){
            if (!variablesUsedForBranchingInThisRectangle.contains(var)) {  
                 variablesUsedForBranchingInThisRectangle.add( var );
            }             
        }
         
        return variablesUsedForBranchingInThisRectangle;
    }

    
    
}

