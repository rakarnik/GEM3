package edu.mit.csail.cgs.deepseq.utilities;

import java.io.File;
import java.io.FileReader;
import java.io.LineNumberReader;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;

import edu.mit.csail.cgs.datasets.general.Region;
import edu.mit.csail.cgs.datasets.species.Genome;
import edu.mit.csail.cgs.deepseq.Read;
import edu.mit.csail.cgs.deepseq.ReadHit;
import edu.mit.csail.cgs.deepseq.StrandedBase;
import edu.mit.csail.cgs.deepseq.discovery.BindingMixture;
import edu.mit.csail.cgs.utils.stats.StatUtil;

/**
 * Modify from AlignmentFileReader.java
 * 
 * Here we use it as a memory cache to store the data.
 * The starts field for each chrom/strand will be distinct. 
 * Multiple reads mapped to the bp position will store as counts.
 * Thus, the count field is different from AlignmentFileReader.java.
 * 
 * This class basically stores the hits coming from the correspoding files. <br>
 * We have made use of an unusual convention for reducing running time purposes. <br>
 * The hits are basically being represented by 3 main fields: <tt>starts, hitCounts</tt>
 * and <tt>hitIDs</tt>. <br>
 * Each of these fields are 3D arrays where:  <br>
 * - the first dimension corresponds to the chromosome that a hit belongs to (based on
 * the mapping from a chromosome as a <tt>String</tt> to an integer via the 
 * <tt>chrom2ID</tt> map).    <br>
 * - the second dimension corresponds to the strand. 0 for '+' (Watson), 1 for '-' (Crick). <br>
 * - the third dimension contains information for a hit (e.g. its start, counts, ID).
 * 
 * @author shaunmahony
 *
 */
public class ReadCache{

	private Genome gen;
	private int numChroms;
	private String name;
	
	private double totalHits;
	private int totalBases;
	private int[] binCounts;			// histogram bins, index: count at bases; value: number of bases.
	private int[] bin500Counts;			// histogram bins, index: count at bases; value: number of bases.
	static final int BINSIZE = 501;
	//Data structures for pre-loading
	
	/**
	 * Chromosomes are stored as IDs for efficiency (saving memory) 
	 */
	//protected int[] chrs=null;
	
	/**
	 * Five prime ends of the read hits. <br>
	 * First dimension represents the corresponding chromosome ID. <br>
	 * Second dimension represents the strand. 0 for '+', 1 for '-' <br>
	 * Third dimension contains the coordinates of the hits
	 */
	private int[][][] fivePrimes=null;
	
	private ArrayList<Integer>[][] fivePrimesList = null;
	
	/**
	 * Number of hits that corresponds to the start position
	 * First dimension represents the corresponding chromosome ID. <br>
	 * Second dimension represents the strand. 0 for '+', 1 for '-' <br>
	 * Third dimension contains the number of hits at corresponding start position 
	 */
	private float[][][] hitCounts=null;
	
	private ArrayList<Float>[][] hitCountsList = null;
	
	/**
	 * Strands of the read hits
	 */
	//protected char[][] strands=null;
	
	private HashMap<String, Integer> chrom2ID=new HashMap<String,Integer>();
	private HashMap<Integer,String> id2Chrom=new HashMap<Integer,String>();
	
	public ReadCache(Genome g, String name){
		totalHits=0;
		totalBases=0;
		gen=g;
		List<String> chromList = g.getChromList();
		numChroms = chromList.size();
		this.name = name;
		
		//Initialize the chromosome name lookup tables
		int i=0; 
		for(String c:chromList){
			chrom2ID.put(c, i);
			id2Chrom.put(i, c);
			i++;
		}
	
		//Initialize the data structures
		fivePrimes    = new int[numChroms][2][];
		hitCounts = new float[numChroms][2][];
		
		fivePrimesList    = new ArrayList[numChroms][2];
		for(i = 0; i < fivePrimesList.length; i++) { for(int j = 0; j < fivePrimesList[i].length; j++) { fivePrimesList[i][j] = new ArrayList<Integer>(); } }
		
		hitCountsList = new ArrayList[numChroms][2];
		for(i = 0; i < hitCountsList.length; i++) { for(int j = 0; j < hitCountsList[i].length; j++) { hitCountsList[i][j] = new ArrayList<Float>(); } }
	}
	
	/**
	 * Loads hits in the region
	 * @param r
	 * @return
	 */
	public List<StrandedBase> getUnstrandedBases(Region r) {
		List<StrandedBase> bases = new ArrayList<StrandedBase>();
		bases.addAll(getStrandedBases(r,'+'));
		bases.addAll(getStrandedBases(r,'-'));
		return bases;
	}
	
	public List<StrandedBase> getStrandedBases(Region r, char strand) {
		List<StrandedBase> bases = new ArrayList<StrandedBase>();
		String chr = r.getChrom();
		int chrID = chrom2ID.get(chr);
		int j = (strand=='+') ? 0 : 1;
		int[] tempStarts = fivePrimes[chrID][j];		
		if(tempStarts.length != 0) {
			int start_ind = Arrays.binarySearch(tempStarts, r.getStart());
			int end_ind   = Arrays.binarySearch(tempStarts, r.getEnd());
			
			if( start_ind < 0 ) { start_ind = -start_ind - 1; }
			if( end_ind < 0 )   { end_ind   = -end_ind - 1; }
			
			start_ind = StatUtil.searchFrom(tempStarts, ">=", r.getStart(), start_ind);
			end_ind   = StatUtil.searchFrom(tempStarts, "<=",   r.getEnd(), end_ind);
			
			for(int k = start_ind; k <= end_ind; k++) {
				bases.add(new StrandedBase(strand, tempStarts[k], hitCounts[chrID][j][k]));
			}	
		}	
		return bases;
	}
	
	public float countHits(Region r) {
		return StrandedBase.countBaseHits(getUnstrandedBases(r));
	}

	/**
	 * Gets the stranded count of all hits (of all chromosomes) for the specified strand
	 * @param strand 
	 * @return
	 */
	protected double getStrandedTotalCount(char strand) {
		int strandInd = strand == '+' ? 0 : 1;
		double count = 0;
		for(int i = 0; i < hitCounts.length; i++) {
			float[] hitCountsTemp = hitCounts[i][strandInd];
			for(float el:hitCountsTemp)
				count += (double)el;
		}
		return count;
	}//end of getStrandedTotalCount method
	
	
	// Add hits to data structure
	// It is called for ReadDB loader, store data loaded from DB
	// It is called multiple times to retrieve all the data, then populateArrays() is called
	public void addHits(String chrom, char strand, Collection<Integer>starts, Collection<Float> counts){
		int chrID   = chrom2ID.get(chrom);
		int strandInd = strand == '+' ? 0 : 1;
		fivePrimesList[chrID][strandInd].addAll(starts);
		hitCountsList[chrID][strandInd].addAll(counts);
		for (float c: counts)
			totalHits += c;
		totalBases += starts.size();
	}//end of addHits method	
	
	// Add all hit starts from all chrom and strand
	// It is called once for file reader that has loaded data into memory
	// assuming the data structure of starts is same as file reader
	public void addAllFivePrimes(ArrayList<int[][][]> allStarts, int readLength){
		for(int i = 0; i < fivePrimesList.length; i++){			// chrom
			for(int j = 0; j < fivePrimesList[i].length; j++){	// strand
				int[][][] tmp = allStarts.get(0);
				int[] allPositions = tmp[i][j];
				tmp[i][j]=null;
				for (int k=1;k<allStarts.size();k++){		// files: duplicates
					tmp = allStarts.get(k);
					allPositions = mergeOrderedList(allPositions, tmp[i][j]);
					tmp[i][j]=null;
				}
				System.gc();
				if (allPositions.length==0)
					continue;
				// consolidate counts of same bp position
				int count = 1;
				int previous = allPositions[0];
				for (int m=1;m<allPositions.length;m++){
					if (allPositions[m]==previous){
						count++;
					}
					else{
						if (j==0)	
							fivePrimesList[i][j].add(previous);				// + strand, start
						else
							fivePrimesList[i][j].add(previous+readLength-1);	// - strand, end
						hitCountsList[i][j].add((float)count);
						count=1;
						previous = allPositions[m];
					}
				}
				// add the last element
				if (j==0)	
					fivePrimesList[i][j].add(previous);				// + strand, start
				else
					fivePrimesList[i][j].add(previous+readLength-1);	// - strand, end
				hitCountsList[i][j].add((float)count);

				// update stats
				totalBases += fivePrimesList[i][j].size();
				for (float c: hitCountsList[i][j])
					totalHits += c;
			}
		}
	}
	private int[] mergeOrderedList(int[]a, int[]b){
		int[] result = new int[a.length+b.length];
		int ai=0; int bi=0;
		for (int i=0;i<result.length;i++){
			if (bi!=b.length && (ai==a.length || a[ai]>b[bi])){
				result[i]=b[bi];
				bi++;
			}
			else{
				result[i]=a[ai];
				ai++;
			}
		}
		return result;
	}
	/**
	 * Converts lists of Integers to integer arrays, deletes the lists for saving memory
	 * all array elements are ordered in terms of the array <tt>starts</tt>.
	 */
	public void populateArrays() {
		for(int i = 0; i < fivePrimesList.length; i++)
			for(int j = 0; j < fivePrimesList[i].length; j++)
				fivePrimes[i][j] = list2int(fivePrimesList[i][j]);
		fivePrimesList = null;
		for(int i = 0; i < hitCountsList.length; i++)
			for(int j = 0; j < hitCountsList[i].length; j++)
				hitCounts[i][j] = list2float(hitCountsList[i][j]);
		hitCountsList = null;
		System.gc();
		
		generateStats();
	}
	public void generateStats(){
		// count readHit numbers in 1bp bins
		int max = 200;
		binCounts = new int[max+1];
		for(int i = 0; i < hitCounts.length; i++)
			for(int j = 0; j < hitCounts[i].length; j++)
				for(int k = 0; k < hitCounts[i][j].length; k++){
					int count = (int)hitCounts[i][j][k];
					count = count>max?max:count;
					binCounts[count]++;
				}
		// count readHit numbers in BINSIZE (500bp) bins
		max = 2000;		
		bin500Counts = new int[max+1];		
		for(int i = 0; i < hitCounts.length; i++){
			String chrom = id2Chrom.get(i);
			int totalLength = gen.getChromLength(chrom);
			for (int j=0;j<totalLength;j+=BINSIZE){
				int start=j+1;
				int end=j+BINSIZE;
				int count = (int)countHits(new Region(gen, chrom, start, end));
				count = count>max?max:count;
				bin500Counts[count]++;
			}
		}	
	}
	
	public void filterBaseBias(int maxPerBP){
	}
	
	private int[] list2int(List<Integer> list) {
		int[] out = new int[list.size()];
		for(int i = 0; i < out.length; i++)
			out[i] = list.get(i);
		return out;
	}
	private float[] list2float(List<Float> list) {
		float[] out = new float[list.size()];
		for(int i = 0; i < out.length; i++)
			out[i] = list.get(i);
		return out;
	}	
	
	//Accessors	
	/**
	 * get the total number of hits (of the all alignment/files)
	 * @return
	 */
	public double getHitCount(){
		return totalHits;
	} 
	public double getBaseCount(){
		return totalBases;
	} 
	public String getName(){
		return name;
	}
	public void printStats(){
		System.out.println("ReadCache\t"+name+"\tBases: "+totalBases+"\tHitCounts: "+totalHits);
	}
	public void printBinCounts(){
		StringBuilder sb = new StringBuilder();
		for (int i=0;i<binCounts.length;i++){
			sb.append(i+"\t"+binCounts[i]+"\n");
		}
		BindingMixture.writeFile(name+"1bpCount.txt", sb.toString());
	}
	public void printBin500Counts(){
		StringBuilder sb = new StringBuilder();
		for (int i=0;i<bin500Counts.length;i++){
			sb.append(i+"\t"+bin500Counts[i]+"\n");
		}
		BindingMixture.writeFile(name+"500bpCount.txt", sb.toString());
	}
	public int getMaxHitPerBP(double fraction){
		double toKeep = (1-fraction) * totalHits;
		int accumulative = 0;
		for (int i=0;i<binCounts.length;i++){
			accumulative += binCounts[i]*i;
			if (accumulative>=toKeep)
				return i;
		}
		return binCounts.length;
	}
}