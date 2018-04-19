/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ca.mcmaster.infeasiblerectangles_v2_bnbr.solvers.analytic;

import ca.mcmaster.infeasiblerectangles_v2_bnbr.BNBR_Driver;
import static ca.mcmaster.infeasiblerectangles_v2_bnbr.Constants.LOG_FILE_EXTENSION;
import static ca.mcmaster.infeasiblerectangles_v2_bnbr.Constants.LOG_FOLDER;
import static ca.mcmaster.infeasiblerectangles_v2_bnbr.Constants.ONE;
import static ca.mcmaster.infeasiblerectangles_v2_bnbr.Constants.TEN;
import static ca.mcmaster.infeasiblerectangles_v2_bnbr.Constants.*;
import static ca.mcmaster.infeasiblerectangles_v2_bnbr.Parameters.*;
import ca.mcmaster.infeasiblerectangles_v2_bnbr.common.LowerBoundConstraint;
import ca.mcmaster.infeasiblerectangles_v2_bnbr.common.Rectangle;
import ca.mcmaster.infeasiblerectangles_v2_bnbr.common.SolutionTree_Node;
import ca.mcmaster.infeasiblerectangles_v2_bnbr.common.VariableCoefficientTuple;
import ca.mcmaster.infeasiblerectangles_v2_bnbr.common.utils.BranchingVariableSuggestor;
import ca.mcmaster.infeasiblerectangles_v2_bnbr.common.utils.RectangleCollectionSplitter;
import ca.mcmaster.infeasiblerectangles_v2_bnbr.common.utils.validator.CplexBasedValidator;
import ilog.concert.IloException;
import ilog.cplex.IloCplex;
import static java.lang.System.exit;
import java.util.ArrayList;
import java.util.Collections;
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
 * similar to depth first solve, tries to arrive at nodes with all variable refcounts==1
 * then solves them by enumeration
 * 
 */
public class Enum_Solver {
    
    //active leafs , key is highest refcount multiplied by -1
    public   Map<Integer, List<SolutionTree_Node> > activeLeafs  = new TreeMap<Integer,  List<SolutionTree_Node> >();
    
    public double incumbent = Double.MAX_VALUE;
    
    public   Map<Double, List<Rectangle> > solutionPool  = new TreeMap<Double,  List<Rectangle> >();
    
    private static Logger logger=Logger.getLogger(Enum_Solver.class);    
    static {
        logger.setLevel(Level.WARN);
        PatternLayout layout = new PatternLayout("%5p  %d  %F  %L  %m%n");     
        try {
            RollingFileAppender appender =new  RollingFileAppender(layout,LOG_FOLDER+Enum_Solver.class.getSimpleName()+ LOG_FILE_EXTENSION);
            appender.setMaxBackupIndex(TEN*TEN);
            logger.addAppender(appender);
            logger.setAdditivity(false);
        } catch (Exception ex) {
            ///
            System.err.println("Exit: unable to initialize logging");       
            exit(1);
        }
        
        
    }
    
    public Enum_Solver (){
        //init
        SolutionTree_Node root = new SolutionTree_Node (new ArrayList<String> (), new ArrayList<String> ()) ;
        List<SolutionTree_Node>  nodes = new ArrayList<SolutionTree_Node> () ;
        nodes.add( root) ;
        root.getLpRelaxVertex_Minimization();
        activeLeafs.put (/*any number as estimate of freq*/ TEN, nodes ) ;
    }
    
    public void solve () throws IloException {
        while (this.activeLeafs.size()>ZERO  ){
            
            printAllLeafs() ;            
            logger.debug(" \n\n" +incumbent  +" incumbent  "+ "numleafs is "+ getNumberOfLeafs(this.activeLeafs));
            
            //get leaf with lowest refcount reamining
            int lowestRefCount = Collections.min(this.activeLeafs.keySet()) ;
            int numberOFLeafsAtLowestRefcount = this.activeLeafs.get( lowestRefCount).size();
            
            List<SolutionTree_Node> bestNodes = this.activeLeafs.get( lowestRefCount);
            SolutionTree_Node selectedNode =removeBestLPNodeFrom (bestNodes) ;
            if (bestNodes.size()==ZERO) {
                this.activeLeafs.remove( lowestRefCount);
            }else {
                this.activeLeafs.put(lowestRefCount, bestNodes);
            }
            
            logger.debug("Selected node is "+ selectedNode);
            System.err.println("Select node is "+ selectedNode.myId + " with lp relax " + selectedNode.lpRelaxValueMinimization + 
                    " and lowest refcount "+ lowestRefCount + " with this many leafs "+ numberOFLeafsAtLowestRefcount);
            
            System.err.flush();
            
            //replenish infeasible rects for every constraint , so that count is either upto allowed max or all are collected
            selectedNode.replenishRectangles() ; 
                   
            printInfeasibleRectangles(selectedNode) ;
            
            //this node can be a solution , require branching, or get cutoff by the incumbent
            double bestLPAmongAllInfeasibleRectangles = selectedNode.getBestLPAmongAllInfeasibleRectangles();
            
            if ( selectedNode.lpRelaxValueMinimization>=(incumbent*(ONE-ENUM_SOLVER_MIP_GAP))){
                //discard
            } else if (selectedNode.lpRelaxValueMinimization<bestLPAmongAllInfeasibleRectangles) {
                //this Lp vertex is a feasible solution
                Rectangle feasibleSoln = new Rectangle (selectedNode.lpVertex_zeroFixedVariables, selectedNode.lpVertex_oneFixedVariables) ;
               
                this.addSolutionToPool(feasibleSoln);
                logger.debug("found rectangular feasible solution " + feasibleSoln + " with value " + feasibleSoln.getLpRelaxVertex_Minimization());
                
                //printInfeasibleRectangles_WARN(selectedNode);
                
                this.validateSolution(feasibleSoln);
                
            }else if (lowestRefCount==ONE) {
                //solution node if solutionVertex is better than LP value of rectangles for all constraints whose rects have not all been collected
                
                //we can get enumerated soln for this node
                List<Rectangle> infeasibleRects = new ArrayList<Rectangle> () ;
                for (TreeMap <Double, List<Rectangle>> treemap:selectedNode.myInfeasibleRectanglesList){
                    for ( List<Rectangle> rects : treemap.values()){
                        infeasibleRects.addAll(rects) ;
                    }                    
                }

                Enumerator enumerator = new Enumerator (  selectedNode.zeroFixedVariables , selectedNode.oneFixedVariables);
                Rectangle soln = enumerator.getEnumeratedSolution(infeasibleRects);
                addSolutionToPool(soln);
                logger.warn ("found solution by enumeration "+ soln) ;
                
                //printInfeasibleRectangles_WARN(selectedNode);
                
                this.validateSolution(soln);
                
                /********
                IMPORTANT - to do 
                ******/
                //if enumerated solution worse than one of the rects for a constraint all of whose rects were not collected, then branch
                
            }else {
                //needs branching
                 //find branching vars
                List<VariableCoefficientTuple> branchingVariables = 
                        (new BranchingVariableSuggestor (   selectedNode,   bestLPAmongAllInfeasibleRectangles) ).getBestChoiceBranchingVariables();
                     //   (new HalferBranchVarSuggestor(selectedNode)).suggest();

                logger.debug("branchingVariables recommendation is ") ;
                for (VariableCoefficientTuple tuple: branchingVariables){
                    logger.debug (tuple) ;
                }
                logger.debug("branchingVariables recommendation SIZE is " + branchingVariables.size()) ;
                
                //create a chain of child nodes
                SolutionTree_Node parentNode = selectedNode;
                for (VariableCoefficientTuple branchingVar: branchingVariables){
                    parentNode = createTwoChildNodes(parentNode,  branchingVar ) ;
                    //if we get a null return - it means no further branching is required because we have created an infeasible rect
                    //
                    //this can happen because our branching variable recommendation chain gaurantees that 
                    //at least 1 rect will be eliminated by these conditions. It is possible that 
                    //only a substring of these instructions already arrives at an infeasible rect
                    if (null==parentNode) break;
                }
                
                
            }
            
             
        } //end while
                
        IloCplex.Status status =getStatus ();
        if (status.equals(IloCplex.Status.Infeasible) ) {
            System.out.println("MIP is infeasible") ;            
        }else {
            System.out.println("MIP is optimal " + incumbent + " \nprinting solution pool\n") ;            
            for (List<Rectangle> solns : this.solutionPool.values()) {
                for (Rectangle soln : solns){
                    System.out.println(soln) ;    
                }                
            }
        }//end get status and print soln
    }
    
        
    public IloCplex.Status getStatus (){
        IloCplex.Status status= IloCplex.Status.Infeasible;
        if (this.solutionPool.size()>ZERO){
            status = IloCplex.Status.Feasible;
            if (!activeLeafs.isEmpty() ){
                if ( Collections.min(this.activeLeafs.keySet())>=incumbent)      status = IloCplex.Status.Optimal;
            }else {
                status = IloCplex.Status.Optimal;
            }
        }
        return status;
    }
    
    private SolutionTree_Node  removeBestLPNodeFrom(List<SolutionTree_Node> nodes) {
        SolutionTree_Node result = null ;
        double bestLP = Double.MAX_VALUE;
        for (SolutionTree_Node  node: nodes){
            if (node.lpRelaxValueMinimization< bestLP){
                result = node;
                bestLP = node.lpRelaxValueMinimization;
            }
        }
        nodes.remove(result);
        return result;
    }
    
    //create two child nodes and return the one which obeys the direction indicated in th etuple, so that it can be branched again
    //note that the other child node may be infeasible, if so do not add into list of active leafs
    private SolutionTree_Node createTwoChildNodes(SolutionTree_Node parentNode, VariableCoefficientTuple branchingVar ) {
        

        //create two child nodes, each with its own list of compatible infeasible rects
        List <String> newZeroFixedVariables = new ArrayList<String> () ;
        List <String>  newOneFixedVariables = new ArrayList<String> () ;
        newZeroFixedVariables.addAll(parentNode.zeroFixedVariables) ;
        newZeroFixedVariables.add(branchingVar.varName) ;
        newOneFixedVariables.add(branchingVar.varName );
        newOneFixedVariables.addAll(parentNode.oneFixedVariables) ;
        SolutionTree_Node zeroChild = new SolutionTree_Node (  newZeroFixedVariables ,  parentNode.oneFixedVariables , 
                                                               parentNode.areAllRectsCollectedForThisConstraint);
        SolutionTree_Node oneChild = new SolutionTree_Node   ( parentNode.zeroFixedVariables  ,  newOneFixedVariables,
                                                               parentNode.areAllRectsCollectedForThisConstraint);

        boolean isZeroChildFeasible = true ;
        boolean isOneChildFeasible = true ;

        //find the collection of infeasible rects for left and right child nodes, and also if they are feasible
        int constraintNumber= -ONE;
        for (LowerBoundConstraint lbc : BNBR_Driver.mipConstraintList){

            constraintNumber++;
                        

            RectangleCollectionSplitter splitter = new RectangleCollectionSplitter  (parentNode.myInfeasibleRectanglesList.get(constraintNumber), 
                                                                                      isZeroChildFeasible , isOneChildFeasible);
            splitter.split(branchingVar.varName , parentNode.zeroFixedVariables ,parentNode.oneFixedVariables  );

            isZeroChildFeasible = isZeroChildFeasible && ! splitter.isZeroSideInfeasible;
            isOneChildFeasible = isOneChildFeasible && ! splitter.isOneSideInfeasible;

            //if a side is feasible , update its rectangle collection, otherwise do not bother
            if (isZeroChildFeasible) {
                zeroChild.setInfeasibleRectangles (constraintNumber, splitter.rectangle_Compatible_With_Zero_Side) ;
            }
            if (isOneChildFeasible) {
                oneChild.setInfeasibleRectangles (constraintNumber, splitter.rectangle_Compatible_With_One_Side) ;
            }   
            
            
            
        }//end for all constraints, updat einfeas rects for each child node

        //add one of these two nodes back into active leafs, the one mathcing the branching var direction is not added because
        //it will be used to branch more child nodes. Also the last remaining node will be unfeasible
        if (isZeroChildFeasible &&( branchingVar.coeff!=ZERO)) {
          
            
            this.addActiveLeaf(zeroChild);
        }
        if (isOneChildFeasible && ( branchingVar.coeff==ZERO)) {
            this.addActiveLeaf(oneChild);
            
        }
        
        //null return informs child node creation loop that we have already created an infeasible node, so looping can stop
        return branchingVar.coeff==ZERO? (isZeroChildFeasible ? zeroChild: null): (isOneChildFeasible? oneChild:null); 
    }
    
    private void  printInfeasibleRectangles(SolutionTree_Node selectedNode) {
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
    /*private void  printInfeasibleRectangles_WARN(SolutionTree_Node selectedNode) {
         logger.warn(" node id is "+ selectedNode.myId);
         int index = -ONE;
         for (Map <Double, List<Rectangle>> rectMap:selectedNode.myInfeasibleRectanglesList) {
             logger.warn("rect list for   constraint " + BNBR_Driver.mipConstraintList.get(++index).name) ;
             for (List<Rectangle> nodes : rectMap.values()) {
                 for (Rectangle node : nodes ){
                     logger.warn(node);
                 }
             }
         }
         
         logger.warn("");
    }*/
    
    private static int getNumberOfLeafs ( Map<Integer, List<SolutionTree_Node> > map2){
        int count = ZERO;
        for (List<SolutionTree_Node> rectlist : map2.values()){
            count +=rectlist.size();
        }
        return count;
    }
    
    private void addActiveLeaf( SolutionTree_Node new_Node ) {
        
         
        //get lp relax, used to find freq
        new_Node.getLpRelaxVertex_Minimization();
        //find highest freq , which is avialable as -ve number
        int freq = - new_Node.getRefcount_of_HighestFreqVar( ) ;
        List<SolutionTree_Node>  nodeList = this.activeLeafs.get( freq);
        if (nodeList==null) nodeList = new ArrayList<SolutionTree_Node> ();
        nodeList.add(new_Node);
        //note sign reversal
        this.activeLeafs.put( freq, nodeList);
    }
    
    private void addSolutionToPool(Rectangle soln) {
        incumbent = Math.min(incumbent, soln.getLpRelaxVertex_Minimization()) ;
        //add only 1 soln per lp relax value
        List<Rectangle>  solnList = /*this.solutionPool.get( soln.lpRelaxValueMinimization);
        if (solnList==null) solnList =*/ new ArrayList<Rectangle> ();
        solnList.add(soln);
        this.solutionPool.put(soln .lpRelaxValueMinimization , solnList);
    }
    
    private void printAllLeafs () {
        logger.debug("");
        for (List<SolutionTree_Node> nodes : this.activeLeafs.values()) {
            for (SolutionTree_Node node: nodes){
                logger.debug(node);
            }
            
        }
        logger.debug("");
        
    }
    
    private void validateSolution(Rectangle soln) throws IloException{
        CplexBasedValidator validator = new CplexBasedValidator (soln.zeroFixedVariables, soln.getLpRelaxVertex_Minimization());
        if (validator.isValid()) {
            //soln will be logged
        }else {
            System.err.println("invalid solution "+ soln) ;
            exit(ONE);
        }
    }
    
}
