/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ca.mcmaster.infeasiblerectangles_v2_bnbr;

/**
 *
 * @author tamvadss
 */
 public class Parameters {
    
       // public static final String MIP_FILENAME = "F:\\temporary files here\\knapsackFourTest.lp";  ////x1 x7  x4  x2
       //public static final String MIP_FILENAME = "F:\\temporary files here\\knapsackSmall.lp";  ////x1 x7  x4  x2
       public static final String MIP_FILENAME = "F:\\temporary files here\\protfold.mps";
       //public static final String MIP_FILENAME = "harp2.mps";
       //public static final String MIP_FILENAME = "cov1075.mps";
       //public static final String MIP_FILENAME = "p6b.mps";

       
       public static boolean USE_STRICT_INEQUALITY_IN_MIP = false;
       
       //for rectangle collection
       public static int MAX__RECTS_TIMES_CONSTRAINTS = 1000000;
       public static int MAX__RECTS_COLLECTED_PER_CONSTRAINT = 1000; 
       public static int RECTS_TO_BE_COLLECTED_PER_CONSTRAINT = 1;  // this is the lower limit, could go up to MAX_RECTS Defined above
       //public static double REPLENISH_THESHOLD_FRACTION = 0.5;
       
       //for branching var selection
       public static double FRACTION_OF_INFERIOR_LP_RECTS_TO_CONSIDER = 0.2; //conser any rect which is 1.x times the LP, or less
               
       //use when solving with cplex  
       public static int        CPLEX_USE_MULTITHREADING_WITH_THIS_MANY_THREADS=1;
       public static int CPLEX_NODEFILE_TO_DISK = 3;
       public static int CPLEX_HUERISTIC_FREQUENCY = -1;
       public static int CPLEX_CUT_PASSES = 0;
       public static boolean CPLEX_PRESOLVE = false;
       public static int CPLEX_REDUCE =0;
       public static boolean USE_MIP_GAP= false;
       public static double MIP_GAP_PERCENT = 0.06;  //6%
       
        
}
