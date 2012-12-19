package edu.mit.csail.cgs.deepseq.analysis;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.TreeMap;

import edu.mit.csail.cgs.datasets.general.Point;
import edu.mit.csail.cgs.datasets.species.Genome;
import edu.mit.csail.cgs.datasets.species.Organism;
import edu.mit.csail.cgs.deepseq.utilities.CommonUtils;
import edu.mit.csail.cgs.tools.utils.Args;
import edu.mit.csail.cgs.utils.NotFoundException;
import edu.mit.csail.cgs.utils.Pair;

public class PairBindingAnalysis {

	/**
	 * Report the relationship between a pair of binding calls: 
	 * <ol>
	 * <li> The distance between bindnig calls
	 * <li> The percentages of intersection relative the whole sets
	 * </ol>
	 * example: --species "Mus musculus;mm9" --TF1 "C:\Data\workspace\gse\CTCF_Kmer_outputs\CTCF_Kmer_2_GEM_events.txt" --TF2 "C:\Data\workspace\gse\CTCF_Kmer_outputs\CTCF_Kmer_1_GEM_events.txt" --win 100 --out TF1_vs_TF2.txt
	 */
	public static void main(String[] args) {
		Genome genome = null;
		try {
	    	Pair<Organism, Genome> pair = Args.parseGenome(args);
	    	if(pair==null){
	    	  System.err.println("No genome provided; provide a Gifford lab DB genome name");
	    	  System.exit(1);
	    	}else{
	    		genome = pair.cdr();
	    	}
	    } catch (NotFoundException e) {
	      e.printStackTrace();
	    }
	    	
		// load binding sites (TF1)
	    ArrayList<String> texts = CommonUtils.readTextFile(Args.parseString(args, "TF1", null));
		// group sites by chrom
		TreeMap<String, ArrayList<Point>> chrom2sites = new TreeMap<String, ArrayList<Point>>();
		for (String line:texts){
			if (line.length()==0)
				continue;
			if (line.startsWith("#")||line.startsWith("Position"))
				continue;
			String[] f = line.split("\\s+");		// match one or more white space
			Point p = Point.fromString(genome, f[0]);
			String chr = p.getChrom();
			if (!chrom2sites.containsKey(chr))
				chrom2sites.put(chr, new ArrayList<Point>());
			chrom2sites.get(chr).add(p);
		}		
		// sort sites in each chrom
		for (String chr: chrom2sites.keySet()){
			ArrayList<Point> sites = chrom2sites.get(chr);
			Collections.sort(sites);
		}
			
		int win = Args.parseInteger(args, "win", 100);
		StringBuilder out = new StringBuilder("#TF1\tTF2\tOffset\n");
		
		// load binding sites (TF2)
	    texts = CommonUtils.readTextFile(Args.parseString(args, "TF2", null));
		for (String line:texts){
			if (line.length()==0)
				continue;
			if (line.startsWith("#")||line.startsWith("Position"))
				continue;
			String[] f = line.split("\\s+");		// match one or more white space
			Point tf2 = Point.fromString(genome, f[0]);
			if (chrom2sites.containsKey(tf2.getChrom())){
				ArrayList<Point> sites = chrom2sites.get(tf2.getChrom());
				ArrayList<Point> results = CommonUtils.getPointsWithinWindow(sites, tf2, win);
				for (Point tf1:results){
					out.append(tf1.toString()).append("\t").append(tf2.toString()).append("\t").append(tf2.offset(tf1)).append("\n");
				}
			}
		}
		
		CommonUtils.writeFile(Args.parseString(args, "out", "out.txt"), out.toString());

	}


}