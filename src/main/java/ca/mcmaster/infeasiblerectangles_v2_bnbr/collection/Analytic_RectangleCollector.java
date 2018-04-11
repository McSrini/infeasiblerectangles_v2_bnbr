/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ca.mcmaster.infeasiblerectangles_v2_bnbr.collection;

import static ca.mcmaster.infeasiblerectangles_v2_bnbr.Constants.*; 
import static ca.mcmaster.infeasiblerectangles_v2_bnbr.Parameters.*;
import ca.mcmaster.infeasiblerectangles_v2_bnbr.common.*;
import ca.mcmaster.infeasiblerectangles_v2_bnbr.common.*;
import static java.lang.System.exit;
import java.util.*;
import org.apache.log4j.*;

/**
 *
 * @author tamvadss
 * 
 *  * collect feasible rectangles using analytical method
 * 
 * maximization mip with 1 UB constraint
 * 
 * origin is minimization lp vertex for supplied treenode, if origin is not included in this feasible region, return empty set
 * 
 * * grow lowest coeff var in constr first, till no more growth possible.
 * 
 *  branch on fixed vars to create a feasible node, and create other nodes which need further decomposition. Branch on highest coeff var in constraint first
 * 
 * reapeat collection procedure on best remaining LP relax node, lp relax being minimization lp relax ( i..e lp relax at origin). 
 * Stop when collection size exceeds a threshold
 * 
 * 
 */
public class Analytic_RectangleCollector {
    
    public   Map<Double, List<CollectedRectangle>  > collectedFeasibleRectangles = new TreeMap <Double, List<CollectedRectangle>>  ();    
    
    //this is the leaf node and its complimented constraint for which we will collect rects
    private UpperBoundConstraint ubc ;
    private SolutionTree_Node leaf;
        
    //here are the nodes which are by products of createing th efeasible node. These need to be
    //decomposed further to get more feasible nodes
    private    Map<Double, List<CollectedRectangle>  > pendingJobs = new TreeMap <Double, List<CollectedRectangle>>  ();    
    
    private static Logger logger=Logger.getLogger(Analytic_RectangleCollector.class);
    
    static {
        logger.setLevel(Level.DEBUG);
        PatternLayout layout = new PatternLayout("%5p  %d  %F  %L  %m%n");     
        try {
            logger.addAppender(new  RollingFileAppender(layout,LOG_FOLDER+Analytic_RectangleCollector.class.getSimpleName()+ LOG_FILE_EXTENSION));
            logger.setAdditivity(false);
        } catch (Exception ex) {
            ///
            System.err.println("Exit: unable to initialize logging");       
            exit(1);
        }
        
        
    }
    
    public Analytic_RectangleCollector (SolutionTree_Node leaf, LowerBoundConstraint lbc){
        
        this.ubc=(new UpperBoundConstraint(  lbc)) ;
        this.leaf=leaf;
        
        //if UBC contains the lp relaxed vertex, only then do we collect rectangles
        double lp = leaf.getLpRelaxVertex_Minimization();        
        UpperBoundConstraint reduced  = this.ubc.getReducedConstraint(leaf.lpVertex_zeroFixedVariables, leaf.lpVertex_oneFixedVariables);
         
        if (reduced.isGauranteedFeasible(!USE_STRICT_INEQUALITY_IN_MIP)){            
            
            CollectedRectangle cr = new CollectedRectangle (leaf.zeroFixedVariables, leaf.oneFixedVariables );
            cr.getLpRelaxVertex_Minimization();
            List<CollectedRectangle> jobList = new ArrayList<CollectedRectangle>();
            jobList.add( cr);
            pendingJobs.put(lp , jobList);
            logger.debug( "leaf id is " + leaf.myId + " and constraint  for collection is " +ubc + " will collect upto this many rects " + RECTS_COLLECTED_PER_CONSTRAINT );
        }else {
            logger.debug( "leaf id is " + leaf.myId + " and constraint  for collection is " +ubc + " will NOT collect rects ");
        }
        
    }
        
    public void collect(int max_Collection_size){
        //remove the best pending node and decompose it, until pending list is empty or #of collected rects exceeds threshold
        while (!this.pendingJobs.isEmpty() && collectedFeasibleRectangles.size() < max_Collection_size) {
            
           printAllJobs();
           
           double bestLPRelax = Collections.min( pendingJobs.keySet());
           List<CollectedRectangle> bestLPJobs = pendingJobs.get(bestLPRelax);
           CollectedRectangle job = bestLPJobs.remove(ZERO);
           if (bestLPJobs.size()==ZERO) {
               pendingJobs.remove(bestLPRelax);
           }else {
               pendingJobs.put(bestLPRelax, bestLPJobs);
           }
           decompose(job);
           
        }
    }
    
    //decompose a leaf node and add feasible node found into collection, and more jobs into joblist
    private void decompose (CollectedRectangle leafNode) {
        
        logger.debug("decomposing job "+ leafNode);
        
        UpperBoundConstraint reducedConstraint = ubc.getReducedConstraint(leafNode.zeroFixedVariables, leafNode.oneFixedVariables);
        
        //if reduced constraint is trivially feasible or unfeasible, no need for dempostion
        if (reducedConstraint.isTrivially_Feasible( !USE_STRICT_INEQUALITY_IN_MIP) || reducedConstraint.isGauranteedFeasible(!USE_STRICT_INEQUALITY_IN_MIP)) {
            this.addLeafToFeasibleCollection(leafNode);
            logger.debug( "Collected whole feasible rect "+leafNode);
        }else if (reducedConstraint.isTrivially_Infeasible( !USE_STRICT_INEQUALITY_IN_MIP) || reducedConstraint.isGauranteed_INFeasible(!USE_STRICT_INEQUALITY_IN_MIP) ){
            //discard
            logger.debug("discard infeasible rect " + leafNode);
        }else {
            //must decompose
            
            //here is the list of values of vars in the reduced constraint , fixed at  0 or 1 at the origin. 
            List<Boolean> isVariableFixedAtZeroAtOrigin = new ArrayList<Boolean>();   
                    
            //initialize list isVariableFixedAtZeroAtOrigin
            double constraintValueAtOrigin = ZERO;
            for (VariableCoefficientTuple tuple: reducedConstraint.sortedConstraintExpr){
                if (this.leaf.lpVertex_zeroFixedVariables.contains( tuple.varName)) {
                    isVariableFixedAtZeroAtOrigin.add( true);
                }else {
                    isVariableFixedAtZeroAtOrigin.add( false);
                    constraintValueAtOrigin+=tuple.coeff;
                }
            }
            
            //for each variable in the constraint, starting with the lowest coeff, see if flipping its value from its origin value will render constraint infeasible
            //continue till we hit infeasibility
            //delta is how much we move from the origin by flipping this variable 
            double delta =ZERO;
            int countOfVarsWhichCanBeFree = ZERO ;
            for (int index = ZERO; index < reducedConstraint.sortedConstraintExpr.size(); index ++){
                VariableCoefficientTuple tuple= reducedConstraint.sortedConstraintExpr.get(index);
                //System.out.println("tuple "+ tuple.varName);
                //if var has +ve coeff in obj and has 0 value at vertex, delta will increase by flipping it
                //if var has -ve coeff in obj and has 1 value at vertex, delta will increase by flipping it
                //in th eother two cases, var is already at its "max" value , so flipping is not a problem
                boolean checkFlip = tuple.coeff >ZERO && isVariableFixedAtZeroAtOrigin.get(index);
                checkFlip = checkFlip || tuple.coeff <ZERO && ! isVariableFixedAtZeroAtOrigin.get(index);
                delta += checkFlip ? Math.abs(tuple.coeff): ZERO;
                
                boolean isConstraintViolated = !USE_STRICT_INEQUALITY_IN_MIP ? 
                        (delta +constraintValueAtOrigin>= reducedConstraint.upperBound): 
                        (delta +constraintValueAtOrigin> reducedConstraint.upperBound);
                if ( isConstraintViolated) {
                    //growth no longer possible
                    break;
                } else {
                    countOfVarsWhichCanBeFree ++;
                    logger.debug("Free var " + tuple.varName) ;
                    //System.out.println("tuple added "+ tuple.var.name);
                }
                
            }//end for
                        
            //now we know the vars which can be free. Note that all vars cannot be free, because entire rectangle feasible has been taken care of
            CollectedRectangle feasibleLeaf = createFeasibleRectangle (leafNode, isVariableFixedAtZeroAtOrigin,countOfVarsWhichCanBeFree ,reducedConstraint) ;
            this.addLeafToFeasibleCollection(feasibleLeaf );
            
            List<CollectedRectangle> newJobs = null;
            if ( countOfVarsWhichCanBeFree!=ZERO) {
                newJobs = createMoreNodesForDecompostion (leafNode, isVariableFixedAtZeroAtOrigin,countOfVarsWhichCanBeFree ,reducedConstraint) ;
                this.addPendingJobs(newJobs);  
            }
                        
            logger.debug( "Collected feasible rect "+feasibleLeaf);
            
        }//end else
    }//end method
    
    private List<CollectedRectangle> createMoreNodesForDecompostion (CollectedRectangle leafNode, List<Boolean> isVariableFixedAtZeroAtOrigin,
                                                          int countOfVarsWhichCanBeFree,  UpperBoundConstraint reducedConstraint ) {
        
        List<CollectedRectangle> newJobs = new ArrayList<CollectedRectangle>();
        
        //starting with the highest coeff var in the reduced constraint, flip var value from origin, and for all higher coeff vars retain their value at origin
        //# of jobs created = (# of freevars in reduced constr - countOfVarsWhichCanBeFree)
        for (int jobIndex = ZERO; jobIndex< reducedConstraint.sortedConstraintExpr.size()- countOfVarsWhichCanBeFree;jobIndex++){
            CollectedRectangle  newJob = new CollectedRectangle(leafNode.zeroFixedVariables, leafNode.oneFixedVariables ) ;
            
            //add flipped  branching condition for jth largest var
            int size = reducedConstraint.sortedConstraintExpr.size();
            if (isVariableFixedAtZeroAtOrigin.get(size-ONE-jobIndex)){
                newJob.oneFixedVariables.add(  reducedConstraint.sortedConstraintExpr.get(size-ONE-jobIndex).varName);
            }else {
                newJob.zeroFixedVariables.add(  reducedConstraint.sortedConstraintExpr.get(size-ONE-jobIndex).varName);
            }
            
            //for all the other higher coeff vars, add branching conditions by using their value at origin
            int numberOfHigherCoeffVars = jobIndex;
            for (; numberOfHigherCoeffVars >ZERO; numberOfHigherCoeffVars--){
                if (isVariableFixedAtZeroAtOrigin.get(size-numberOfHigherCoeffVars)) {
                    newJob.zeroFixedVariables.add(reducedConstraint.sortedConstraintExpr.get(size-numberOfHigherCoeffVars).varName);
                }else {
                    newJob.oneFixedVariables.add( reducedConstraint.sortedConstraintExpr.get(size-numberOfHigherCoeffVars).varName);
                }
            }
                    
            newJobs.add(newJob );
        }
        
        return newJobs ;
    }
        
    private CollectedRectangle createFeasibleRectangle (CollectedRectangle leafNode, List<Boolean> isVariableFixedAtZeroAtOrigin,
                                               int countOfVarsWhichCanBeFree, UpperBoundConstraint reducedConstraint  ) {
        //create a branch using vars which are not free, and fix their values to their value at origin
        //origin is lp vertex
        CollectedRectangle result = new CollectedRectangle (leafNode.zeroFixedVariables, leafNode.oneFixedVariables ) ;
        for (int index = countOfVarsWhichCanBeFree; index < isVariableFixedAtZeroAtOrigin.size(); index ++) {
            //get var value at origin, and fix it at that value
            if (isVariableFixedAtZeroAtOrigin.get(index)){
                result.zeroFixedVariables.add(  reducedConstraint.sortedConstraintExpr.get(index).varName);
            }else {
                result.oneFixedVariables.add(  reducedConstraint.sortedConstraintExpr.get(index).varName);
            }
        }
        
         
        return result ;
    }
    
    
    private void addLeafToFeasibleCollection (CollectedRectangle leafNode){
        List<CollectedRectangle> rects= collectedFeasibleRectangles.get( leafNode.getLpRelaxVertex_Minimization());
        if (rects==null) rects =new ArrayList<CollectedRectangle> ();
        rects.add(leafNode) ;
        collectedFeasibleRectangles.put( leafNode.lpRelaxValueMinimization, rects);
    }
    
    private void addPendingJobs (List<CollectedRectangle> jobs) {
        for (CollectedRectangle job : jobs){
            addPendingJob(job);
        }
    }
    
    private void addPendingJob (CollectedRectangle job) {
        List<CollectedRectangle> rects= this.pendingJobs.get(job.getLpRelaxVertex_Minimization());
        if (rects==null) rects =new ArrayList<CollectedRectangle> ();
        rects.add(job) ;
        this.pendingJobs.put( job.lpRelaxValueMinimization, rects);
    }
    
        
    private void printAllJobs() {
        for (List<CollectedRectangle> jobList : this.pendingJobs.values()){
            for (CollectedRectangle  job: jobList){
                logger.debug(job);
            }
        }
    }
}
