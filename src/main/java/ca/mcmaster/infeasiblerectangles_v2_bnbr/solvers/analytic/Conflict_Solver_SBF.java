/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ca.mcmaster.infeasiblerectangles_v2_bnbr.solvers.analytic;

import ca.mcmaster.infeasiblerectangles_v2_bnbr.BNBR_Driver;
import static ca.mcmaster.infeasiblerectangles_v2_bnbr.Constants.DOUBLE_ZERO;
import static ca.mcmaster.infeasiblerectangles_v2_bnbr.Constants.LOG_FILE_EXTENSION;
import static ca.mcmaster.infeasiblerectangles_v2_bnbr.Constants.LOG_FOLDER;
import static ca.mcmaster.infeasiblerectangles_v2_bnbr.Constants.ONE;
import static ca.mcmaster.infeasiblerectangles_v2_bnbr.Constants.TEN;
import static ca.mcmaster.infeasiblerectangles_v2_bnbr.Constants.ZERO; 
import ca.mcmaster.infeasiblerectangles_v2_bnbr.common.LowerBoundConstraint;
import ca.mcmaster.infeasiblerectangles_v2_bnbr.common.Rectangle;
import ca.mcmaster.infeasiblerectangles_v2_bnbr.common.SolutionTree_Node;
import ca.mcmaster.infeasiblerectangles_v2_bnbr.common.TwoIntegerTuple;
import ca.mcmaster.infeasiblerectangles_v2_bnbr.common.VariableCoefficientTuple;
import ca.mcmaster.infeasiblerectangles_v2_bnbr.common.utils.ConflictVariableSuggestor;
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
 */
public class Conflict_Solver_SBF {
        
    public double incumbent = Double.MAX_VALUE;
    public Rectangle bestKnownSolution = null;
    
    //active leafs , key is  LP
    public   Map<Double, List<SolutionTree_Node> > activeLeafs  = new TreeMap<Double,  List<SolutionTree_Node> >();
    
    private static Logger logger=Logger.getLogger(Conflict_Solver_SBF.class);    
    static {
        logger.setLevel(Level.WARN);
        PatternLayout layout = new PatternLayout("%5p  %d  %F  %L  %m%n");     
        try {
            RollingFileAppender appender =new  RollingFileAppender(layout,LOG_FOLDER+Conflict_Solver_SBF.class.getSimpleName()+ LOG_FILE_EXTENSION);
            appender.setMaxBackupIndex(TEN*TEN);
            logger.addAppender(appender);
            logger.setAdditivity(false);
        } catch (Exception ex) {
            ///
            System.err.println("Exit: unable to initialize logging");       
            exit(1);
        }
    }
    
    public Conflict_Solver_SBF(){
        //init
        SolutionTree_Node root =new SolutionTree_Node (new ArrayList <String> (),new ArrayList <String> ());
        List<SolutionTree_Node>  nodes = new ArrayList<SolutionTree_Node> () ;
        nodes.add( root) ;
        root.getLpRelaxVertex_Minimization();
        activeLeafs.put (/*any number as estimate */ DOUBLE_ZERO, nodes ) ;
    }
     
    public void solve () throws IloException {
        while (this.activeLeafs.size()>ZERO  ){
            
            printAllLeafs() ;            
            logger.debug(" \n\n" +incumbent  +" incumbent  "+ "numleafs is "+ getNumberOfLeafs(this.activeLeafs));
            
            //get leaf with lowest rectangle count reamining
            double lowestLP  = Collections.min(this.activeLeafs.keySet()) ;
            
            List<SolutionTree_Node> bestNodes = this.activeLeafs.get( lowestLP );
            SolutionTree_Node selectedNode =removeBestLPNodeFrom (bestNodes) ;
            if (bestNodes.size()==ZERO) {
                this.activeLeafs.remove( lowestLP );
            }else {
                this.activeLeafs.put(lowestLP , bestNodes);
            }
            
            logger.debug("Selected node is "+ selectedNode);
            System.err.println("Select node is "+ selectedNode.myId + " with lp relax " + selectedNode.lpRelaxValueMinimization + 
                    " and rcetangle count "+ lowestLP + " with this many leafs reamining at this count"+ bestNodes.size());
            
            System.err.flush();
            
            //replenish infeasible rects for every constraint , so that count is either upto allowed max or all are collected
            selectedNode.replenishRectangles() ;                    
            printInfeasibleRectangles(selectedNode) ;
            
            //this node can be a solution , require branching, or get cutoff by the incumbent
            double bestLPAmongAllInfeasibleRectangles = selectedNode.getBestLPAmongAllInfeasibleRectangles();            
            if ( selectedNode.lpRelaxValueMinimization>=incumbent*(ONE )){
                //discard
            } else if (selectedNode.lpRelaxValueMinimization<bestLPAmongAllInfeasibleRectangles) {
                //this Lp vertex is a feasible solution
                Rectangle feasibleSoln = new Rectangle (selectedNode.lpVertex_zeroFixedVariables, selectedNode.lpVertex_oneFixedVariables) ;                
                logger.warn("found   feasible solution vertex " + feasibleSoln + " with value " + feasibleSoln.getLpRelaxVertex_Minimization());                
                this.validateSolution(feasibleSoln);          
                this.addSolutionToPool(feasibleSoln);
            } else {
                //prepare to branch
                ConflictVariableSuggestor varSuggestor=new ConflictVariableSuggestor ( selectedNode  );
                TwoIntegerTuple childRectCounts = new TwoIntegerTuple ();
                VariableCoefficientTuple branchingVariableSuggestion = varSuggestor.suggestBranchingVariable(childRectCounts);
                
                if (Double.compare(branchingVariableSuggestion.coeff,ONE)==ZERO){
                    //solve this leaf node by enumeration
                    //we can get enumerated soln for this node
                    List<Rectangle> infeasibleRects = new ArrayList<Rectangle> () ;
                    for (TreeMap <Double, List<Rectangle>> treemap:selectedNode.myInfeasibleRectanglesList){
                        for ( List<Rectangle> rects : treemap.values()){
                            infeasibleRects.addAll(rects) ;
                        }                    
                    }

                    Enumerator enumerator = new Enumerator (  selectedNode.zeroFixedVariables , selectedNode.oneFixedVariables);
                    Rectangle soln = enumerator.getEnumeratedSolution(infeasibleRects);
                    logger.warn ("found solution by enumeration "+ soln) ;

                    this.validateSolution(soln);
                    this.addSolutionToPool( soln);

                    /********
                    IMPORTANT - to do 
                    ******/
                    //if enumerated solution worse than one of the rects for a constraint all of whose rects were not collected, then branch
                }else {
                    //create the 2 child nodes
                    this.createTwoChildNodes(selectedNode, branchingVariableSuggestion, childRectCounts);
                }
                
            } //end if-else what to do with selected node           
            
        }//end while leafs still remain
                
        IloCplex.Status status =getStatus ();
        if (status.equals(IloCplex.Status.Infeasible) ) {
            System.out.println("MIP is infeasible") ;    
        } else if (status.equals(IloCplex.Status.Feasible)){
            System.out.println("MIP is feasible " + incumbent + " best known solution " + bestKnownSolution) ;            
        }else {
            System.out.println("MIP is optimal " + incumbent + " \nprinting optimal\n"+ bestKnownSolution) ;            
             
        }//end get status and print soln
    }
            
    public IloCplex.Status getStatus (){
        IloCplex.Status status= IloCplex.Status.Infeasible;
        if (bestKnownSolution!=null){
            status = IloCplex.Status.Feasible;
            if ( activeLeafs.isEmpty() ){                 
                status = IloCplex.Status.Optimal;
            }
        }
        return status;
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
    
    private static int getNumberOfLeafs ( Map<Double, List<SolutionTree_Node> > map2){
        int count = ZERO;
        for (List<SolutionTree_Node> rectlist : map2.values()){
            count +=rectlist.size();
        }
        return count;
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
    
    private void addActiveLeaf( SolutionTree_Node new_Node  ) {        
         
        //get lp relax 
        new_Node.getLpRelaxVertex_Minimization();
        
        List<SolutionTree_Node>  nodeList = this.activeLeafs.get( new_Node.lpRelaxValueMinimization);
        if (nodeList==null) nodeList = new ArrayList<SolutionTree_Node> ();
        nodeList.add(new_Node);
        
        this.activeLeafs.put( new_Node.lpRelaxValueMinimization, nodeList);
    }
       
    private void addSolutionToPool(Rectangle soln) {
        if ( soln.getLpRelaxVertex_Minimization() < incumbent) {
            incumbent =  soln.lpRelaxValueMinimization;
            this.bestKnownSolution= soln;
        }
        
    } 
    
    private void createTwoChildNodes(SolutionTree_Node parentNode, VariableCoefficientTuple branchingVar, TwoIntegerTuple childRectCounts ) {        

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
                zeroChild.setInfeasibleRectangles (constraintNumber, splitter.rectangles_Compatible_With_Zero_Side) ;
            }
            if (isOneChildFeasible) {
                oneChild.setInfeasibleRectangles (constraintNumber, splitter.rectangles_Compatible_With_One_Side) ;
            }  
            
        }//end for all constraints, update infeasible rects for each child node

        //add these two nodes back into active leafs, 
        if (isZeroChildFeasible ) {
            this.addActiveLeaf(zeroChild );
        }
        if (isOneChildFeasible) {
            this.addActiveLeaf(oneChild );            
        }
    }//end method create child nodes
    
}//end class
