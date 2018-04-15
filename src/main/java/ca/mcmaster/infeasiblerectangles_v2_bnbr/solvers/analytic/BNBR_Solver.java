/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ca.mcmaster.infeasiblerectangles_v2_bnbr.solvers.analytic;

import ca.mcmaster.infeasiblerectangles_v2_bnbr.BNBR_Driver;
import static ca.mcmaster.infeasiblerectangles_v2_bnbr.Constants.*;
import ca.mcmaster.infeasiblerectangles_v2_bnbr.common.*;
import ca.mcmaster.infeasiblerectangles_v2_bnbr.common.utils.BranchingVariableSuggestor;
import ca.mcmaster.infeasiblerectangles_v2_bnbr.common.utils.RectangleCollectionSplitter;
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
 */
public class BNBR_Solver {
    
    public   Map<Double, List<SolutionTree_Node> > activeLeafs  = new TreeMap<Double,  List<SolutionTree_Node> >();
    
    public double incumbent = Double.MAX_VALUE;
    
    public   Map<Double, List<Rectangle> > solutionPool  = new TreeMap<Double,  List<Rectangle> >();
    
    private static Logger logger=Logger.getLogger(BNBR_Solver.class);    
    static {
        logger.setLevel(Level.OFF);
        PatternLayout layout = new PatternLayout("%5p  %d  %F  %L  %m%n");     
        try {
            RollingFileAppender appender =new  RollingFileAppender(layout,LOG_FOLDER+BNBR_Solver.class.getSimpleName()+ LOG_FILE_EXTENSION);
            appender.setMaxBackupIndex(TEN*TEN);
            logger.addAppender(appender);
            logger.setAdditivity(false);
        } catch (Exception ex) {
            ///
            System.err.println("Exit: unable to initialize logging");       
            exit(1);
        }
        
        
    }
    
    public BNBR_Solver (){
        //init
        SolutionTree_Node root = new SolutionTree_Node (new ArrayList<String> (), new ArrayList<String> ()) ;
        List<SolutionTree_Node>  nodes = new ArrayList<SolutionTree_Node> () ;
        nodes.add( root) ;
        root.getLpRelaxVertex_Minimization();
        activeLeafs.put ( root.lpRelaxValueMinimization, nodes ) ;
    }
    
    public void solve () {
        
        //pick best leaf and remove from active set
        // if we dont have infes rects for any constraint, try to replenish them
        //have a flag F sayin have we got all infes rects for a given constr
        // if lp vertex better than best infes rect, vertex is optimal, add to pool and update incumbent
        //else 
        //check if no infes rects  and fully feasible , then collect soln . Note that feasibility cheks require all flags F to be true
        //else
        //must branch 
        //pick best branching var as moost shared among highest lp infes rects, if var has refcount 1 enumerat ethe soln
        // else create two child nodes using split map and insert both into active leafs
        //
        
        
        while (this.activeLeafs.size()>ZERO && getBestBound()< incumbent){
            
            printAllLeafs() ;            
            logger.debug(" \n\nbest bound is "+ getBestBound() + " incumbent is " +incumbent + " numleafs is "+ getNumberOfLeafs(this.activeLeafs));
             
            
            //pluck the best reamaining leaf
            double bestBound = getBestBound();
            List<SolutionTree_Node> bestNodes = this.activeLeafs.get( bestBound);
            SolutionTree_Node selectedNode =bestNodes.remove(ZERO);
            if (bestNodes.size()==ZERO) {
                this.activeLeafs.remove( bestBound);
            }else {
                this.activeLeafs.put(bestBound, bestNodes);
            }
            
            logger.debug("Selected node is "+ selectedNode);
            System.err.println("Select node is "+ selectedNode.myId + " with lp relax " + selectedNode.lpRelaxValueMinimization);
            System.err.flush();
            
            //replenish infeasible rects for every constraint , so that count is either upto allowed max or all are collected
            selectedNode.replenishRectangles() ; 
                   
            printInfeasibleRectangles(selectedNode) ;
            
            //note that infeasible leafs are not inserted back into list of active leafs, so we 
            //only have to check for optimality before branching
            
            //if node LP vertex has a better value than any compatible infeasible rect, collect the node LP vertex as a solution and discard this node
            double bestLPAmongAllInfeasibleRectangles = selectedNode.getBestLPAmongAllInfeasibleRectangles();
            if ( selectedNode.lpRelaxValueMinimization<bestLPAmongAllInfeasibleRectangles){
                
                //****
                //Warning note - note strict inequailty , equality could mean that this LP vertex is included in an infeasible rect.
                //**
                
                //Also since we are doing best-first, this will be optimal
                Rectangle optimalSolution = new Rectangle (selectedNode.lpVertex_zeroFixedVariables, selectedNode.lpVertex_oneFixedVariables) ;
                this.addSolutionToPool(optimalSolution);
                 
                logger.debug("found optimal solution " + optimalSolution);
                break ; //end while active leafs left
            } else {
                //must branch
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
            
            
        }//end while
        
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
        
    }//end method solve
    
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
        
    public double getBestBound () {
        return Collections.min(this.activeLeafs.keySet()) ;
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
    
    private static int getNumberOfLeafs ( Map<Double, List<SolutionTree_Node> > map2){
        int count = ZERO;
        for (List<SolutionTree_Node> rectlist : map2.values()){
            count +=rectlist.size();
        }
        return count;
    }
    
    private void addSolutionToPool(Rectangle soln) {
        incumbent = Math.min(incumbent, soln.getLpRelaxVertex_Minimization()) ;
        List<Rectangle>  solnList = this.solutionPool.get( soln.lpRelaxValueMinimization);
        if (solnList==null) solnList = new ArrayList<Rectangle> ();
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
    
    private void addActiveLeaf( SolutionTree_Node new_Node) {
        List<SolutionTree_Node>  nodeList = this.activeLeafs.get( new_Node.getLpRelaxVertex_Minimization());
        if (nodeList==null) nodeList = new ArrayList<SolutionTree_Node> ();
        nodeList.add(new_Node);
        this.activeLeafs.put( new_Node.lpRelaxValueMinimization , nodeList);
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
        
    private static int getNumberOfRects ( Map<Double, List<Rectangle> > map2){
        int count = ZERO;
        for (List<Rectangle> rectlist : map2.values()){
            count +=rectlist.size();
        }
        return count;
    }
        
    private int getNumberOFInfeasibleRectangles (SolutionTree_Node treeNode) {
        int count = ZERO;
        
        for (TreeMap <Double, List<Rectangle>> rectMap: treeNode.myInfeasibleRectanglesList) {
            count += getNumberOfRects(rectMap) ;
        }
        
        return count;
    }
    
    
}
