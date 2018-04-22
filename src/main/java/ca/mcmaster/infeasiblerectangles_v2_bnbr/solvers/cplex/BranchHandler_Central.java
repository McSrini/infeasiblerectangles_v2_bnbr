/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ca.mcmaster.infeasiblerectangles_v2_bnbr.solvers.cplex;

import ca.mcmaster.infeasiblerectangles_v2_bnbr.BNBR_Driver;
import static ca.mcmaster.infeasiblerectangles_v2_bnbr.Constants.*;
import ca.mcmaster.infeasiblerectangles_v2_bnbr.common.LowerBoundConstraint;
import ca.mcmaster.infeasiblerectangles_v2_bnbr.common.Rectangle;
import ca.mcmaster.infeasiblerectangles_v2_bnbr.common.SolutionTree_Node;
import ca.mcmaster.infeasiblerectangles_v2_bnbr.common.TwoIntegerTuple;
import ca.mcmaster.infeasiblerectangles_v2_bnbr.common.TwoStringTuple;
import ca.mcmaster.infeasiblerectangles_v2_bnbr.common.VariableCoefficientTuple;
import ca.mcmaster.infeasiblerectangles_v2_bnbr.common.utils.ConflictVariableSuggestor;
import ca.mcmaster.infeasiblerectangles_v2_bnbr.common.utils.RectangleCollectionSplitter;
import ilog.concert.IloException;
import ilog.concert.IloNumVar;
import ilog.cplex.IloCplex;
import ilog.cplex.IloCplex.NodeId;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import static java.lang.System.exit;
import java.util.List;
import java.util.*;

/**
 *
 * @author tamvadss
 * //gets branching var recommendation for this node, and overrules CPLEX branching
 */
public class BranchHandler_Central extends IloCplex.BranchCallback {
    
    private List<TreeMap <Double, List<Rectangle>>>  originalInfeasibleRects=null;
    private Map<String, IloNumVar> mapOfVariablesInModel = new HashMap<String, IloNumVar>();
    
    private int numConstraints=ZERO;
 
    //init with initial list of infeasible rects
    public BranchHandler_Central (List<TreeMap <Double, List<Rectangle>> > initialInfeasibleRects, IloNumVar[] variablesInModel) {
        this.originalInfeasibleRects=initialInfeasibleRects;
        for (IloNumVar var :variablesInModel ){
            mapOfVariablesInModel.put (var.getName(), var) ;
        }
     
        numConstraints =initialInfeasibleRects.size();
    }
    
    protected void main() throws IloException {
        if ( getNbranches()> 0 ){  
            
            if (null==getNodeData()){
                //root of mip
                NodeAttachment data = new NodeAttachment (   );
                setNodeData(data);                
            } 
            //node data has branching conditions for this node            
            NodeAttachment nodeData = (NodeAttachment) getNodeData();
            
            //calculate rectangles compatible to this node
            List<TreeMap <Double, List<Rectangle>>>  myInfeasibleRects =getMyCompatibleInfeasibleRects(nodeData) ;
             
            //get branching var 
            String branchingVar = getBranchingVariableSuggestion (  nodeData, myInfeasibleRects);
            
            //branch
            branch ( branchingVar ,   nodeData) ;
             
        }
    }
    
    
    private TwoStringTuple branch (String branchingVarName,  NodeAttachment nodeData) throws IloException{
        
        TwoStringTuple childNodeIDs = new TwoStringTuple();
        
        // branches about to be created
        IloNumVar[][] vars = new IloNumVar[TWO][] ;
        double[ ][] bounds = new double[TWO ][];
        IloCplex.BranchDirection[ ][]  dirs = new  IloCplex.BranchDirection[ TWO][];
        
        //get var with given name, and create up and down branch conditions
        vars[ZERO] = new IloNumVar[ONE];
        vars[ZERO][ZERO]= this.mapOfVariablesInModel.get(branchingVarName );
        bounds[ZERO]=new double[ONE ];
        bounds[ZERO][ZERO]=ZERO;
        dirs[ZERO]= new IloCplex.BranchDirection[ONE];
        dirs[ZERO][ZERO]=IloCplex.BranchDirection.Down;

        vars[ONE] = new IloNumVar[ONE];
        vars[ONE][ZERO]= this.mapOfVariablesInModel.get(branchingVarName );
        bounds[ONE]=new double[ONE ];
        bounds[ONE][ZERO]=ONE;
        dirs[ONE]= new IloCplex.BranchDirection[ONE];
        dirs[ONE][ZERO]=IloCplex.BranchDirection.Up;
        
        for (int childNum = ZERO ;childNum<TWO;  childNum++) {  
            boolean isZeroFix =( Math.round (bounds[childNum][ZERO]) == ZERO); // zero bound indicates binary var is fixed at 0
            NodeAttachment thisChild  =  new NodeAttachment (branchingVarName,isZeroFix , nodeData.zeroFixedVars, nodeData.oneFixedVars  ); 
            NodeId id = makeBranch( vars[childNum],  bounds[childNum],dirs[childNum], getObjValue(), thisChild);
            //System.out.println("Branching on "+branchingVarName) ;
            if (isZeroFix) childNodeIDs.zeroID=id.toString() ; else childNodeIDs.oneID=id.toString();
        }//end for 2 kids
        
        return childNodeIDs;
    }//end method branch
    
    
    private String getBranchingVariableSuggestion (NodeAttachment nodeData, List<TreeMap <Double, List<Rectangle>>>  myInfeasibleRects) {
        //create a node representing this cplex node
        SolutionTree_Node thisNode= new SolutionTree_Node (nodeData.zeroFixedVars, nodeData.oneFixedVars);
        //set its infeasible rectangle list
        thisNode.myInfeasibleRectanglesList= myInfeasibleRects;
        //invoke the variable suggestor
        ConflictVariableSuggestor varSuggestor=new ConflictVariableSuggestor (  thisNode  );
        TwoIntegerTuple childRectCounts = new TwoIntegerTuple ();
        VariableCoefficientTuple branchingVariableSuggestion = varSuggestor.suggestBranchingVariable(childRectCounts);
        return branchingVariableSuggestion.varName;
    }
    
    private  List<TreeMap <Double, List<Rectangle>>>  getMyCompatibleInfeasibleRects(NodeAttachment nodeData) {
        List<TreeMap <Double, List<Rectangle>>> result = new ArrayList<TreeMap <Double, List<Rectangle>>>();
        for (int index = ZERO; index < this.numConstraints; index++) {
            result.add( new TreeMap <Double, List<Rectangle>>());
        }
        for (int index = ZERO; index < this.numConstraints; index++) {
            TreeMap <Double, List<Rectangle>> rectMap = this.originalInfeasibleRects.get(index);
            //fliter out incompatible rects
            TreeMap <Double, List<Rectangle>> filteredRectMap =new TreeMap <Double, List<Rectangle>>();
            for (Double key : rectMap.keySet()) {
                List<Rectangle> originalList = rectMap.get(key);
                List<Rectangle> newList= getFilteredRects (originalList,nodeData ) ;
                if (  newList.size()>ZERO) filteredRectMap.put (key, newList) ;
            }
            result.set(index, filteredRectMap);
        }
        
        return result;
    }
    
    private  List<Rectangle>  getFilteredRects (List<Rectangle> originalList , NodeAttachment nodeData ) {
        List<Rectangle> newList = new ArrayList<Rectangle> ();
        for (Rectangle rect: originalList){
            if (isCompatible(  rect,   nodeData) ) {
                newList.add(rect);
            }
        }
        return newList;
    }
    
    private boolean isCompatible(Rectangle rect, NodeAttachment nodeData) {
        boolean isCompatible = true;
        //if any of the rect zero fixed vars are in node 1 fixed vars, it is incompatible
        //similarly for rect 1 fixed vars
        
        for (String zeroVar : rect.zeroFixedVariables){
            if (nodeData.oneFixedVars.contains(zeroVar)){
                isCompatible=false;
                break;
            }
        }
        
        for (String oneVar: rect.oneFixedVariables ){
            if(nodeData.zeroFixedVars.contains(oneVar)){
                isCompatible=false;
                break;
            }
        }
        
        return isCompatible;
    }
  
}//end class
