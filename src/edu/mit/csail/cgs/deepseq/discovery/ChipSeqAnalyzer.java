package edu.mit.csail.cgs.deepseq.discovery;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.Vector;

import edu.mit.csail.cgs.datasets.chipseq.ChipSeqLocator;
import edu.mit.csail.cgs.datasets.species.Genome;
import edu.mit.csail.cgs.datasets.species.Organism;
import edu.mit.csail.cgs.deepseq.DeepSeqExpt;
import edu.mit.csail.cgs.deepseq.utilities.CommonUtils;
import edu.mit.csail.cgs.tools.utils.Args;
import edu.mit.csail.cgs.utils.ArgParser;
import edu.mit.csail.cgs.utils.NotFoundException;
import edu.mit.csail.cgs.utils.Pair;

public class ChipSeqAnalyzer{
	public final static String GPS_VERSION = "1.0";
	private boolean development_mode = false;

	private String[] args;
	private Genome genome;
	private int readLength=-1;
	
	private BindingMixture mixture=null;
	
	ChipSeqAnalyzer(String[] args){
		System.out.println("Welcome to GPS (version "+GPS_VERSION+")!");
		if(args.length==0){ 
			printHelp(); 
			System.exit(1); 
		}
		this.args = args;
		ArgParser ap = new ArgParser(args);
		Set<String> flags = Args.parseFlags(args);
		development_mode = flags.contains("dev");
		if ( (ap.hasKey("help"))) {
			printHelp();
			System.exit(1);
		}

		try {
			if(ap.hasKey("species")){
				Pair<Organism, Genome> pair = Args.parseGenome(args);
				if(pair != null)
					genome = pair.cdr();
			}else{
				//Make fake genome... chr lengths provided???
				if(ap.hasKey("g")){
					genome = new Genome("Genome", new File(ap.getKeyValue("g")));
	        	}else{
	        		genome=null;
	        	}
			}
		} catch (NotFoundException e) {
			e.printStackTrace();
		}
		
		// checking all required parameters
		if (! (ap.hasKey("d"))) {
			printError();
			System.err.println("The read distribution file is required. Use --d option.\n");
			System.exit(1);
		}
		if (! (ap.hasKey("s"))) {
			System.err.println("The mappable genome size is required.\n");
			printError();
			System.exit(1);
		}
		String modelFile = Args.parseString(args, "d", null);	// read distribution file
		if (modelFile!=null){
			File pFile = new File(modelFile);
			if(!pFile.isFile()){
				System.err.println("\nCannot find read distribution file!");
				System.exit(1);
			}
		}

		System.out.println("Loading data...");
        //Experiments : Load each condition expt:ctrl Pair
		ArrayList<Pair<DeepSeqExpt,DeepSeqExpt>> experiments = new ArrayList<Pair<DeepSeqExpt,DeepSeqExpt>>();
		long loadData_tic = System.currentTimeMillis();
		ArrayList<String> conditionNames = new ArrayList<String>();
		int exptHitCount=0;
		int ctrlHitCount=0;
		Vector<String> exptTags=new Vector<String>();
		for(String s : args)
        	if(s.contains("expt"))
        		if(!exptTags.contains(s))
        			exptTags.add(s);
		
		if(exptTags.size()==0){
		    System.err.println("Error: No signal experiments provided.\nUse the --expt option.");
		    printError();
		    System.exit(1);
		}
        // each tag represents a condition
        for(String tag : exptTags){
        	String name="";
        	if(tag.startsWith("--rdb")){
        		name = tag.replaceFirst("--rdbexpt", ""); 
        		conditionNames.add(name);
        	}else{
        		name = tag.replaceFirst("--expt", ""); 
        		conditionNames.add(name);
        	}

        	if(name.length()>0)
        		System.out.println("    loading condition: "+name);
        	
        	List<ChipSeqLocator> rdbexpts = Args.parseChipSeq(args,"rdbexpt"+name);
        	List<ChipSeqLocator> rdbctrls = Args.parseChipSeq(args,"rdbctrl"+name);
        	List<File> expts = Args.parseFileHandles(args, "expt"+name);
        	List<File> ctrls = Args.parseFileHandles(args, "ctrl"+name);  
        	boolean nonUnique = ap.hasKey("nonunique") ? true : false;
        	String fileFormat = Args.parseString(args, "f", "BED").toUpperCase();

        	if(expts.size()>0 && rdbexpts.size()==0){
        		readLength = -1;	// For file, read length will be obtained from the data
	        	DeepSeqExpt e = new DeepSeqExpt(genome, expts, nonUnique, fileFormat, readLength);
	        	DeepSeqExpt c = new DeepSeqExpt(genome, ctrls, nonUnique, fileFormat, readLength);
        		if(genome==null){
	        		genome = DeepSeqExpt.combineFakeGenomes(e,c);
	        		e.setGenome(genome);
	        		c.setGenome(genome);
	        	}
        		experiments.add(new Pair<DeepSeqExpt,DeepSeqExpt>(e,c));
	        	exptHitCount+=e.getHitCount();
	        	ctrlHitCount+=c.getHitCount();
	        }
	        else if(rdbexpts.size()>0 && expts.size() == 0){
	        	if(genome==null){
        			System.err.println("Error: the genome must be defined in order to use the Gifford Lab DB."); 
        			System.exit(1);
        		}
	    		readLength = -1;
	        	experiments.add(new Pair<DeepSeqExpt,DeepSeqExpt>(new DeepSeqExpt(genome, rdbexpts, "readdb", readLength),new DeepSeqExpt(genome, rdbctrls, "readdb", readLength)));
	        }
	        else{
	        	System.err.println("Must provide either an aligner output file or Gifford lab DB experiment name for the signal experiment (but not both)");
	        	printError();
	        	System.exit(1);
	        }
        }
        System.out.println("    done: "+CommonUtils.timeElapsed(loadData_tic));
        try{
	    mixture = new BindingMixture(genome, experiments, conditionNames, args);
        }
        catch(Exception ex){
        	for(Pair<DeepSeqExpt,DeepSeqExpt> e : experiments){
				e.car().closeLoaders();
				e.cdr().closeLoaders();
			}
        	ex.printStackTrace();
        }
	}//end of ChipSeqAnalyzer constructor
	
	public void runMixtureModel(){
		
		double kl=10;
		int round = 0;
		String peakFileName = mixture.getOutName();
		mixture.setOutName(peakFileName+"_"+round);
		
//		mixture.countNonSpecificReads();
		int update_model_round = Args.parseInteger(args,"r", 3);
		while (kl>-5 && round<=update_model_round){
			System.out.println("\n============================ Round "+round+" ============================");
			mixture.execute();
			mixture.printFeatures();
			mixture.printFilteredFeatures();
			mixture.printInsignificantFeatures();
			
			round++;
			mixture.setOutName(peakFileName+"_"+round);

			if (round==1){
				boolean fixModelRange = Args.parseFlags(args).contains("fix_model_range");
				if (!fixModelRange){
					Pair<Integer, Integer> newEnds = mixture.getModel().getNewEnds();
					kl = mixture.updateBindingModel(newEnds.car(), newEnds.cdr());
				}
				else
					kl = mixture.updateBindingModel(-mixture.getModel().getMin(), mixture.getModel().getMax());
			}
			else
				kl = mixture.updateBindingModel(-mixture.getModel().getMin(), mixture.getModel().getMax());
//			int newMax = mixture.getModel().findNewMax();
//			if (newMax!=mixture.getModel().getMax() && round==1){
//				kl = mixture.updateBindingModel(mixture.getModel().getWidth()-1-newMax, newMax);
//			}
		}
		round--;
		mixture.setOutName(peakFileName+"_"+round);
		if (development_mode){
			mixture.printExpandedPeaks(10);
			mixture.addAnnotations();
//			mixture.printPsortedFeatures();
//			mixture.printPsortedCondFeatures();
		}
		mixture.printFeatures();
		mixture.printInsignificantFeatures();
		mixture.printFilteredFeatures();
		mixture.plotAllReadDistributions();
		mixture.closeLogFile();
		//		mixture.printPeakSequences();
//		mixture.writeDebugFile();
		System.out.println("Finished! Binding events are printed to: "+mixture.getOutName()+"_GPS_significant.txt");
	}
	
	public static void main(String[] args){
		long tic = System.currentTimeMillis();
		ChipSeqAnalyzer analyzer = new ChipSeqAnalyzer(args);
		analyzer.runMixtureModel();
		analyzer.close();
		System.out.println("\nTotal running time: "+CommonUtils.timeElapsed(tic)+"\n");
	}

	/**
	 * Command-line help
	 */
	public void printHelp() {
		System.err.print("" +
                "GPS Usage                      (more at http://cgs.csail.mit.edu/gps/)\n" +
//                "   Using with Gifford Lab DB:\n" +
//                "      --species <organism name;genome version>\n"+
//                "      --dbexptX <IP expt (X is condition name)>\n" +
//                "      --dbctrlX <background expt (X is condition name)>\n" +
//                "      --readlen <read length>\n" +
                "   Required parameters:\n" +
                "      --d <read distribution file>\n" +
                "      --s <size of mappable genome in bp>\n" +
                "      --exptX <aligned reads file for expt (X is condition name)>\n" +
                "      --ctrlX <aligned reads file for ctrl (X is condition name)>\n" +
                "   Optional parameters:\n" +
                "      --f <read file format BED/BOWTIE/ELAND/NOVO (default BED)>\n" +
                "      --g <genome info file with chr name/length pairs>\n" +
                "      --r <max times to refine read distribution (default=3)>\n" +
                "      --a <minimum alpha value for sparse prior (default=6)>\n" +
                "      --q <significance level for q-value, specify as -log10(q-value), (default=2, q-value=0.01)>\n" +
                "      --out <output file base name>\n" +
                "   Optional flags: \n" +
                "      --fa <use a fixed user-specified alpha value for all the regions>\n" +
                "      --help <print help information and exit>\n" +
                "\n   Output format:\n" +
                "      The output file contains eight fields in a tab-delimited file:\n" +
                "        - Binding event coordinate\n" +
                "        - IP read count\n" +
                "        - Control read count\n" +
                "        - Fold enrichment (IP/Control)\n" +                
                "        - P-value\n" +
                "        - Q-value (multiple hypothesis corrected)\n"+
                "        - Shape deviation from the empirical read distribution (log10(KL))\n" +
                "        - Shape deviation between IP vs Control (log10(KL))\n" +
                "\n");	
	}
	public void printError() {
		printHelp();
		
		StringBuffer sb = new StringBuffer();
		sb.append("\nYour input options are:\n");
		for (String arg:args){
			if (arg.trim().indexOf(" ")!=-1)
				sb.append("\"").append(arg).append("\" ");
			else
				sb.append(arg).append(" ");
		}
		System.err.println(sb.toString()+"\n");
	}

	//Cleanup the loaders
	//Call this before exiting
	public void close(){
		mixture.cleanup();
	}
	
}
