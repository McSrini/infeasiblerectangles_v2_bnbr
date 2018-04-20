/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ca.mcmaster.infeasiblerectangles_v2_bnbr.solvers.analytic;
  
import static ca.mcmaster.infeasiblerectangles_v2_bnbr.Constants.*;
import ca.mcmaster.infeasiblerectangles_v2_bnbr.common.*;
import   ca.mcmaster.infeasiblerectangles_v2_bnbr.BNBR_Driver;
import java.util.*;
import java.util.List;

/**
 *
 * @author tamvadss
 * 
 * given a set of vars, finds the best fixing for them that does not violate the corresponding infeasible constraint rectangle
 * Basically picks best fixing if feasible, else second best fixing
 * 
 */
public class Enumerator {
     
    private List <String> nodeZeroFixedVariables = new ArrayList <String>();
    private List <String> nodeOneFixedVariables = new ArrayList <String>();
    
    public Enumerator ( List <String> nodeZeroFixedVariables , List <String> nodeOneFixedVariables) {
         
        this.nodeZeroFixedVariables=nodeZeroFixedVariables;
        this.nodeOneFixedVariables=nodeOneFixedVariables;
    }
    
    
    //if a node has no shared vars among compatible infeasible rects, then enumerated soln possible for this node
    public Rectangle getEnumeratedSolution (List<Rectangle>  infeasibleRectangles     ) {
        
        //create empty rect
         Rectangle soln = new Rectangle ( new ArrayList<String> () ,  new ArrayList<String> () );
         //add in vars fixed at this node
         soln.zeroFixedVariables.addAll(nodeZeroFixedVariables);
         soln.oneFixedVariables.addAll(nodeOneFixedVariables);
         
        
         
         for (Rectangle infeasibleRect:  infeasibleRectangles) {
             
             //if this rectangle has any variables which are free, we can grow them to their best value
             //if their best value is this infeasible rectangle, then simply get the second best possible combination of vars
             
             Rectangle delta = getEnumeratedSolution (   infeasibleRect.zeroFixedVariables,   infeasibleRect.oneFixedVariables);
             soln.zeroFixedVariables.addAll(delta.zeroFixedVariables );
             soln.oneFixedVariables.addAll( delta.oneFixedVariables);
         }
         soln.getLpRelaxVertex_Minimization();
         return soln;
         
    }
    
        
    //if a rectangle has no shared vars among compatible infeasible rects, then enumerated soln possible for this rectangle
    //input is the zero and one fixing sthat are infeasible
    private Rectangle getEnumeratedSolution (List <String> zeroFixedVariablesInTheInfeasileRect, List <String> oneFixedVarsInTheInfeasibleRect) {
        
        Rectangle bestSolution =  getBestVarFixings(zeroFixedVariablesInTheInfeasileRect,   oneFixedVarsInTheInfeasibleRect) ;
         
        //if best solution var fixings are a   match with the infeasible rect var fixings, then we go for the 2nd best soln
        //else just return the best solution
        //
        
         //there is bug which was found and fixed here:
         //suppose you have 2 rects, (x1=0, x2=0) and (x1=0, x3=0) of which x1=0 is a branching condition for the node
         //Since our best solution only contains choices for  free vars, if al the free var choices are included in the infeasible rect, the choice is invalid
         //WE have to go for the second best fixing for these free vars in such cases
         //Note the if consition below: if best solution a subset of vars fixed in infeasible rect, then go for 2nd best soln
        
        //
        if (/*bestSolution.zeroFixedVariables.containsAll(zeroFixedVariablesInTheInfeasileRect )&&
            bestSolution.oneFixedVariables.containsAll(oneFixedVarsInTheInfeasibleRect)  &&*/
            oneFixedVarsInTheInfeasibleRect.containsAll(bestSolution.oneFixedVariables )  &&
            zeroFixedVariablesInTheInfeasileRect.containsAll( bestSolution.zeroFixedVariables)    ) {
            
            //use second best solution, which gauranteed to exist ( if rect is invalid then one variable flipped has to be valid).
            bestSolution =  getSecondBestVarFixings(bestSolution);
            
        }else {
            //use best solution
        }
        
        return bestSolution;
        
    }
    
    //for each variable , if its positive choose it to 0 else to 1
    private Rectangle getBestVarFixings (List <String> zeroFixedVariables, List <String> oneFixedVariables   ) {
        Rectangle bestSolution =new Rectangle ( new ArrayList<String> () ,  new ArrayList<String> () );
          
        List <String> allVars = new ArrayList<String> ();
        allVars.addAll(zeroFixedVariables );
        allVars.addAll(oneFixedVariables );
        
        for (String var : allVars) {
            
            //if var is fixed by node branching consditions, we cannot choose its value
            if (this.nodeZeroFixedVariables.contains(var) ) {
                //bestSolution.zeroFixedVariables.add(var);
                continue;
            }
            if (this.nodeOneFixedVariables.contains(var)) {
                //bestSolution.oneFixedVariables.add(var);
                continue; 
            }
            
            //if obj coeff > 0 , choose 0 fixing else choose 1 fixing
            double objCoeff = BNBR_Driver.objective.getObjectiveCoeff(var);
            if (objCoeff>ZERO){
                bestSolution.zeroFixedVariables.add(var);
            }else {
                bestSolution.oneFixedVariables.add(var);
            }
        }
        
        return bestSolution;
    }
    
    //change the free variable with the lowest objective coeff
    private Rectangle getSecondBestVarFixings (Rectangle bestSolution) {
        
        Rectangle secondBestSolution =new Rectangle (bestSolution.zeroFixedVariables, bestSolution.oneFixedVariables);
        
        String varToFlip = getFreeVarWithLowestObjectiveCoeff(bestSolution.zeroFixedVariables, bestSolution.oneFixedVariables);
        
        //flip this var in th ebest solution 
        boolean varFound = false ;
        for (String str : bestSolution.zeroFixedVariables) {
            if (varToFlip.equals(str)){
                varFound = true;
                secondBestSolution.zeroFixedVariables.remove(str);
                secondBestSolution.oneFixedVariables.add(str);
                break;
            }
        }
        for (String str : bestSolution.oneFixedVariables) {
            if (true ==varFound)  break;
            if (varToFlip.equals(str)){
                varFound = true;
                secondBestSolution.oneFixedVariables.remove(str);
                secondBestSolution.zeroFixedVariables.add(str);
                break;
            }
        }
        
        return secondBestSolution;        
    }
    
    
    private String getFreeVarWithLowestObjectiveCoeff(List <String> zeroFixedVariables, List <String> oneFixedVariables){
        String bestVar = null;
        Double bestValSoFar = Double.MAX_VALUE;
        
        List <String> vars = new ArrayList<String> () ;
        vars.addAll( zeroFixedVariables);
        vars.addAll( oneFixedVariables);
        
        //remove all vars which are fixed by branching consditions for this node
        //actually these will never be included in the args passed in
        for (String var: this.nodeZeroFixedVariables) {
            vars.remove(var);
        }
        for (String var : this.nodeOneFixedVariables) {
            vars.remove(var);
        }
         
        for (String  var: vars){
            double thisVal = BNBR_Driver.objective.getObjectiveCoeff(var);
            if ( Math.abs( thisVal) < Math.abs(bestValSoFar)) {
                bestValSoFar=thisVal;
                bestVar= var;
            }
        }      
        
        return bestVar;
    }
    
}
