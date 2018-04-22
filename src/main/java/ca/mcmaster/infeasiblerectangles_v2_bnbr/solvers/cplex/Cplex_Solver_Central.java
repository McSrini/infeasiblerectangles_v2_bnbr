/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ca.mcmaster.infeasiblerectangles_v2_bnbr.solvers.cplex;

import static ca.mcmaster.infeasiblerectangles_v2_bnbr.Constants.ONE;
import static ca.mcmaster.infeasiblerectangles_v2_bnbr.Constants.THREE;
import static ca.mcmaster.infeasiblerectangles_v2_bnbr.Constants.ZERO;
import static ca.mcmaster.infeasiblerectangles_v2_bnbr.Parameters.*;
import ca.mcmaster.infeasiblerectangles_v2_bnbr.common.Rectangle;
import ca.mcmaster.infeasiblerectangles_v2_bnbr.common.SolutionTree_Node;
import ilog.concert.IloException;
import ilog.concert.IloLPMatrix;
import ilog.concert.IloNumVar;
import ilog.cplex.IloCplex;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 *
 * @author tamvadss
 * 
 * branches on recommended var, which is designed to remove rectangles with best   LP
 * 
 * maintains a central repository of infeasible rects to conserve memory
 * 
 */
public class Cplex_Solver_Central {
    
    private IloCplex cplex;
    private IloNumVar[] variablesInModel;
    //map of infeasible rectangles for original MIP
    private  List<TreeMap <Double, List<Rectangle>>>  mapOfInfeasibleRects = null;
    
    
    public Cplex_Solver_Central () throws IloException{
        //import mip into ilocplex
        cplex = new IloCplex ();
        cplex.importModel(MIP_FILENAME);
        IloLPMatrix lpMatrix = (IloLPMatrix)cplex.LPMatrixIterator().next();
        variablesInModel  =lpMatrix.getNumVars();
        
        SolutionTree_Node root =new SolutionTree_Node (new ArrayList <String> (),new ArrayList <String> ());
        root.replenishRectangles();
        mapOfInfeasibleRects =root.myInfeasibleRectanglesList;
        
        BranchHandler_Central bh = new BranchHandler_Central( mapOfInfeasibleRects, variablesInModel) ;
        cplex.use(bh) ;
        
        cplex.setParam(IloCplex.Param.MIP.Strategy.File, THREE); //disk compressed
        cplex.setParam(  IloCplex.IntParam.HeurFreq , -ONE);
        cplex.setParam(IloCplex.Param.MIP.Limits.CutPasses,ZERO);        
        cplex.setParam(IloCplex.Param.Preprocessing.Presolve, false);
        //cplex.setParam(IloCplex.Param.Emphasis.MIP,  ONE);
    }
    
    public void solve () throws IloException{
        cplex.solve();
    }
    
}
