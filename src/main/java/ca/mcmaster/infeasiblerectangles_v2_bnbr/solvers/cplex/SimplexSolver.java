/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ca.mcmaster.infeasiblerectangles_v2_bnbr.solvers.cplex;

import static ca.mcmaster.infeasiblerectangles_v2_bnbr.Constants.*;
import static ca.mcmaster.infeasiblerectangles_v2_bnbr.Parameters.*;
import ca.mcmaster.infeasiblerectangles_v2_bnbr.common.Rectangle;
import ca.mcmaster.infeasiblerectangles_v2_bnbr.common.SolutionTree_Node;
import ca.mcmaster.infeasiblerectangles_v2_bnbr.solvers.analytic.BNBR_Solver;
import ilog.concert.IloException;
import ilog.cplex.IloCplex;
import static java.lang.System.exit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;
import org.apache.log4j.RollingFileAppender;

/**
 *
 * @author tamvadss
 * 
 * uses CPLEX only to find LP relaxations
 * 
 */
public class SimplexSolver {
    
    
    
    public IloCplex cplex;
    
    public   Map<Double, List<SolutionTree_Node> > activeLeafs  = new TreeMap<Double,  List<SolutionTree_Node> >();
    
    public double incumbent = Double.MAX_VALUE;
    
    public   Map<Double, List<Rectangle> > solutionPool  = new TreeMap<Double,  List<Rectangle> >();
    
    private static Logger logger=Logger.getLogger(SimplexSolver.class);    
    static {
        logger.setLevel(Level.OFF);
        PatternLayout layout = new PatternLayout("%5p  %d  %F  %L  %m%n");     
        try {
            logger.addAppender(new  RollingFileAppender(layout,LOG_FOLDER+SimplexSolver.class.getSimpleName()+ LOG_FILE_EXTENSION));
            logger.setAdditivity(false);
        } catch (Exception ex) {
            ///
            System.err.println("Exit: unable to initialize logging");       
            exit(1);
        }
        
        
    }
    
    public SimplexSolver () throws IloException{
        cplex = new IloCplex ();
        cplex.importModel(MIP_FILENAME);
        
        cplex.setParam(IloCplex.Param.MIP.Strategy.File, THREE);
        cplex.setParam(  IloCplex.IntParam.HeurFreq , -ONE);
        cplex.setParam(IloCplex.Param.Threads, ONE); 
        cplex.setParam(IloCplex.Param.MIP.Limits.CutPasses,ZERO);        
        cplex.setParam(IloCplex.Param.Preprocessing.Presolve, false);
        
        
                //init
        SolutionTree_Node root = new SolutionTree_Node (new ArrayList<String> (), new ArrayList<String> ()) ;
        List<SolutionTree_Node>  nodes = new ArrayList<SolutionTree_Node> () ;
        nodes.add( root) ;
        root.getLpRelaxVertex_Minimization();
        activeLeafs.put ( root.lpRelaxValueMinimization, nodes ) ;

  //      cplex.use(new IncumbentHandler()) ;
    //    cplex.use(new NodeHandler(root)) ;
//        cplex.use(new BranchHandler(root)) ;
                
    }
    
}
