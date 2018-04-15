/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ca.mcmaster.infeasiblerectangles_v2_bnbr.solvers.cplex;

import static ca.mcmaster.infeasiblerectangles_v2_bnbr.Constants.ZERO;
import ca.mcmaster.infeasiblerectangles_v2_bnbr.common.SolutionTree_Node;
import ilog.concert.IloException;
import ilog.cplex.IloCplex.BranchCallback;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 *
 * @author tamvadss
 */
public class BranchHandler extends BranchCallback {
    
    private Map<Double, List<SolutionTree_Node> > activeLeafs;
    private SolutionTree_Node root;
    
    public BranchHandler (Map<Double, List<SolutionTree_Node> > activeLeafs, SolutionTree_Node root) {
        this.root = root;
        this.activeLeafs=activeLeafs;
    }

    protected void main() throws IloException {
        
        if ( getNbranches()> ZERO   ){  
                       
            //get the node attachment for this node 
            if (null==getNodeData()){
                //root of mip                                                      
                setNodeData(root);                
            } 
            SolutionTree_Node nodeData = (SolutionTree_Node) getNodeData();
            
            
            
        }
         
    }
    
}
