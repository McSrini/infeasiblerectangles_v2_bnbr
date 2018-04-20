/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ca.mcmaster.infeasiblerectangles_v2_bnbr;

import static ca.mcmaster.infeasiblerectangles_v2_bnbr.Constants.HALT_FILE;
import static ca.mcmaster.infeasiblerectangles_v2_bnbr.Constants.LOG_FOLDER;
import static ca.mcmaster.infeasiblerectangles_v2_bnbr.Constants.*; 
import static ca.mcmaster.infeasiblerectangles_v2_bnbr.Parameters.*;
import static ca.mcmaster.infeasiblerectangles_v2_bnbr.Parameters.MIP_FILENAME;
import ca.mcmaster.infeasiblerectangles_v2_bnbr.collection.Analytic_RectangleCollector;
import ca.mcmaster.infeasiblerectangles_v2_bnbr.common.*;
import ca.mcmaster.infeasiblerectangles_v2_bnbr.common.utils.*;
import ca.mcmaster.infeasiblerectangles_v2_bnbr.common.Objective;
import ca.mcmaster.infeasiblerectangles_v2_bnbr.solvers.analytic.BNBR_Solver;
import ca.mcmaster.infeasiblerectangles_v2_bnbr.solvers.analytic.BN_LowestRef_Solver;
import ca.mcmaster.infeasiblerectangles_v2_bnbr.solvers.analytic.Conflict_Solver;
import ca.mcmaster.infeasiblerectangles_v2_bnbr.solvers.analytic.Conflict_Solver_SBF;
import ca.mcmaster.infeasiblerectangles_v2_bnbr.solvers.analytic.Enum_Solver;
import ca.mcmaster.infeasiblerectangles_v2_bnbr.solvers.analytic.bucket.BucketSolver;
import ilog.concert.IloException;
import ilog.concert.IloLPMatrix;
import ilog.concert.IloLinearNumExpr;
import ilog.concert.IloLinearNumExprIterator;
import ilog.concert.IloNumVar;
import ilog.concert.IloObjective;
import ilog.concert.IloObjectiveSense;
import ilog.concert.IloRange;
import ilog.cplex.IloCplex;
import java.io.File;
import static java.lang.System.exit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;
import org.apache.log4j.RollingFileAppender;

/**
 *
 * @author tamvadss
 */
public class BNBR_Driver {
    
    //constraints in this mip
    public static List<LowerBoundConstraint> mipConstraintList ;
    public static Objective objective ;
    public static List<String> allVariablesInModel ;
    
    
    
    private static Logger logger=Logger.getLogger(BNBR_Driver.class);
    
        
    public static void main(String[] args) throws Exception {
                
        if (! isLogFolderEmpty()) {
            System.err.println("\n\n\nClear the log folder before starting " + LOG_FOLDER);
            //exit(ONE);
        }
            
        logger=Logger.getLogger(BNBR_Driver.class);
        logger.setLevel(Level.WARN);
        
        PatternLayout layout = new PatternLayout("%5p  %d  %F  %L  %m%n");     
        try {
            RollingFileAppender rfa = new  RollingFileAppender(layout,LOG_FOLDER+BNBR_Driver.class.getSimpleName()+ LOG_FILE_EXTENSION);
           
            rfa.setMaxBackupIndex(TEN*TEN);
            logger.addAppender(rfa);
            logger.setAdditivity(false);
            
        } catch (Exception ex) {
            System.err.println("Exit: unable to initialize logging");        
            exit(1);
        }
        
        logger.debug ("Start  solve " +MIP_FILENAME ) ;
        
        //assemble constraints in model
        IloCplex mip =  new IloCplex();
        mip.importModel(MIP_FILENAME);
        mipConstraintList= getConstraints(mip);
        objective= getObjective(mip);
        allVariablesInModel = getVariables(mip) ;
        
        RECTS_TO_BE_COLLECTED_PER_CONSTRAINT  = Math.max(Math.round( MAX__RECTS_TIMES_CONSTRAINTS/mipConstraintList.size()), ONE);
        if (MAX__RECTS_COLLECTED_PER_CONSTRAINT < RECTS_TO_BE_COLLECTED_PER_CONSTRAINT) RECTS_TO_BE_COLLECTED_PER_CONSTRAINT=MAX__RECTS_COLLECTED_PER_CONSTRAINT;
        
        
        System.out.println ("Starting solution for ... " + MIP_FILENAME + " RECTS_TO_BE_COLLECTED_PER_CONSTRAINT "+RECTS_TO_BE_COLLECTED_PER_CONSTRAINT) ;
        
        //test
        /*SolutionTree_Node root =new SolutionTree_Node (new ArrayList <String> (),new ArrayList <String> ());
        root.replenishRectangles();
        printInfeasibleRectangles(root);
        ConflictVariableSuggestor varSuggestor=new ConflictVariableSuggestor ( root );
        TwoIntegerTuple childRectCounts = new TwoIntegerTuple ();
        VariableCoefficientTuple suggestion = varSuggestor.suggestBranchingVariable(childRectCounts);*/
        
        //BNBR_Solver solver = new BNBR_Solver () ;
        //Enum_Solver solver = new Enum_Solver () ;
        //BN_LowestRef_Solver solver = new BN_LowestRef_Solver ();
        //BucketSolver solver = new BucketSolver();
        Conflict_Solver_SBF solver = new Conflict_Solver_SBF() ;
        solver.solve();
        System.out.println("The MIP incumbent is = " +solver.incumbent) ;
        
    }//end main
    
    private static List<String> getVariables (IloCplex cplex) throws IloException{
        List<String> result = new ArrayList<String>();
        IloLPMatrix lpMatrix = (IloLPMatrix)cplex.LPMatrixIterator().next();
        IloNumVar[] variables  =lpMatrix.getNumVars();
        for (IloNumVar var :variables){
            result.add(var.getName()) ;
        }
        return result;
    }
    
    //minimization objective
    private static Objective getObjective (IloCplex cplex) throws IloException {
        
        List<VariableCoefficientTuple>   objectiveExpr = new ArrayList<VariableCoefficientTuple>   ();
        
        IloObjective  obj = cplex.getObjective();
        boolean isMaximization = obj.getSense().equals(IloObjectiveSense.Maximize);
        
        IloLinearNumExpr expr = (IloLinearNumExpr) obj.getExpr();
                 
        IloLinearNumExprIterator iter = expr.linearIterator();
        while (iter.hasNext()) {
           IloNumVar var = iter.nextNumVar();
           double val = iter.getValue();
           
           //convert  maximization to minimization 
           VariableCoefficientTuple tuple = new VariableCoefficientTuple (var.getName(), !isMaximization ? val : -val);
           //logger.debug ("Obj " + tuple.coeff + "*" + tuple.varName) ;
           objectiveExpr.add(tuple );
        }
        
        return new Objective (objectiveExpr) ;
        
         
    }
     
    //get all constraints as lower bounds
    private static List<LowerBoundConstraint> getConstraints(IloCplex cplex) throws IloException{
        
        List<LowerBoundConstraint> result = new ArrayList<LowerBoundConstraint>();
        
        IloLPMatrix lpMatrix = (IloLPMatrix)cplex.LPMatrixIterator().next();
        for (IloRange rangeConstraint : lpMatrix.getRanges()){            
             
            boolean isUpperBound = Math.abs(rangeConstraint.getUB())<Double.MAX_VALUE ;
            boolean isLowerBound = Math.abs(rangeConstraint.getLB())<Double.MAX_VALUE ;
            boolean isEquality = rangeConstraint.getUB()==rangeConstraint.getLB();
            boolean isRange = isUpperBound && isLowerBound && !isEquality;
            boolean isUpperBoundOnly =  isUpperBound && !isLowerBound  ;
            boolean isLowerBoundOnly =!isUpperBound && isLowerBound ;
            //equality constraints will be converted into 2 UB constraints - not handled right now
                        
            LowerBoundConstraint lbc = null;
            if ( isUpperBoundOnly || isLowerBoundOnly ) {
                
                //convert upper bound to lower bound  
                double pseudoBound = isUpperBound? -rangeConstraint.getUB(): rangeConstraint.getLB();
                IloLinearNumExprIterator constraintIterator =    ((IloLinearNumExpr) rangeConstraint.getExpr()).linearIterator();
                //this will be our representation of this constarint
                List<VariableCoefficientTuple>   constraintExpr = new ArrayList<VariableCoefficientTuple> ();
                while (constraintIterator.hasNext()) {
                    String varName = constraintIterator.nextNumVar().getName();
                    Double coeff =  constraintIterator.getValue();
                    constraintExpr.add(new VariableCoefficientTuple(varName, isUpperBound? -coeff: coeff));
                }
                
                //here is the constraint, in our format
                
                 
                
                
                lbc  = new LowerBoundConstraint (rangeConstraint.getName(),   constraintExpr,   pseudoBound ) ;
                //add it to our list of constraints
                result.add(lbc);
                //logger.debug(lbc);
                
            }    else     if (isEquality) {
                     
                //we will add two constraints , one LB and one UB           
                IloLinearNumExprIterator constraintIterator =    ((IloLinearNumExpr) rangeConstraint.getExpr()).linearIterator();
                //this will be our representation of this constarint
                List<VariableCoefficientTuple>   constraintExprUB = new ArrayList<VariableCoefficientTuple> ();
                List<VariableCoefficientTuple>   constraintExprLB = new ArrayList<VariableCoefficientTuple> ();
                while (constraintIterator.hasNext()) {
                    String varName = constraintIterator.nextNumVar().getName();
                    Double coeff =  constraintIterator.getValue();
                    constraintExprLB.add(new VariableCoefficientTuple(varName, coeff));
                    constraintExprUB.add(new VariableCoefficientTuple(varName,   -coeff));
                }
                                
                //here is the LB constraint, in our format
                lbc  = new LowerBoundConstraint (rangeConstraint.getName()+NAME_FOR_EQUALITY_CONSTRAINT_LOWER_BOUND_PORTION,
                                                 constraintExprLB,   rangeConstraint.getLB() ) ;
                //add it to our list of constraints
                result.add(lbc);  
                //logger.debug(lbc);
                //second constraint which is UB
                lbc  = new LowerBoundConstraint (rangeConstraint.getName()+NAME_FOR_EQUALITY_CONSTRAINT_UPPER_BOUND_PORTION,
                                                 constraintExprUB,  - rangeConstraint.getUB() ) ;
                //add it to our list of constraints
                result.add(lbc); 
                //logger.debug(lbc);
                          
            } else if (isUpperBound && isLowerBound && !isEquality) {
                System.err.println("Range constraints not allowed -LATER ");
                exit(ONE);
                // such constraints are not read by cplex.import, it seeems
                /*
                //range constraint, create 2 constraints
                IloLinearNumExprIterator constraintIterator =    ((IloLinearNumExpr) rangeConstraint.getExpr()).linearIterator();
                //this will be our representation of this constarint
                List<VariableCoefficientTuple>   constraintExpr_UB = new ArrayList<VariableCoefficientTuple> ();
                List<VariableCoefficientTuple>   constraintExpr_LB = new ArrayList<VariableCoefficientTuple> ();
                while (constraintIterator.hasNext()) {
                    String varName = constraintIterator.nextNumVar().getName();
                    Double coeff =  constraintIterator.getValue();
                    constraintExpr_UB.add(new VariableCoefficientTuple(varName,   coeff ));
                    constraintExpr_LB.add(new VariableCoefficientTuple(varName,  - coeff ));
                }
                ubc  = new UpperBoundConstraint (rangeConstraint.getName()+"U",   constraintExpr_UB,    rangeConstraint.getUB()  ) ;
                //add it to our list of constraints
                result.add(ubc);
                //logger.debug(ubc);
                
                //now add the LB constraint
                ubc  = new UpperBoundConstraint (rangeConstraint.getName()+"L",   constraintExpr_LB,   - rangeConstraint.getLB()  ) ;
                //add it to our list of constraints
                result.add(ubc);
                //logger.debug(ubc);
                */  
            }
                   
        }//end for
        
        return result;
        
    }//end method
    
    private static void  printInfeasibleRectangles(SolutionTree_Node selectedNode) {
         logger.debug("");
         int index = -ONE;
         for (Map <Double, List<Rectangle>> rectMap:selectedNode.myInfeasibleRectanglesList) {
             logger.debug("rect list for   constraint " + BNBR_Driver.mipConstraintList.get(++index).name) ;
             for (List<Rectangle> nodes : rectMap.values()) {
                 for (Rectangle node : nodes ){
                     logger.debug(node);
                 }
             }
         }
         
         logger.debug("");
    }
        
    private static boolean isHaltFilePresent (){
        File file = new File( HALT_FILE);
         
        return file.exists();
    }
        
    private static boolean isLogFolderEmpty() {
        File dir = new File (LOG_FOLDER );
        return (dir.isDirectory() && dir.list().length==ZERO);
    }
}
