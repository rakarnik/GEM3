package edu.mit.csail.cgs.ewok.verbs.motifs;

import java.util.*;
import edu.mit.csail.cgs.datasets.general.Region;
import edu.mit.csail.cgs.ewok.verbs.Expander;
import edu.mit.csail.cgs.ewok.verbs.Mapper;
import edu.mit.csail.cgs.ewok.verbs.SequenceGenerator;
import edu.mit.csail.cgs.datasets.motifs.*;
import edu.mit.csail.cgs.utils.Pair;
import edu.mit.csail.cgs.utils.sequence.SequenceUtils;
import edu.mit.csail.cgs.utils.stats.StatUtil;

public class WeightMatrixScorer implements Mapper<Region,WeightMatrixScoreProfile> {
	public static final int RC = 999;
    private WeightMatrix matrix;
    private SequenceGenerator seqgen;
    
    public WeightMatrixScorer(WeightMatrix m) {
    	matrix = m;
        seqgen = new SequenceGenerator();
    }
    /**
     * Constructor that will cache the genome sequence, useful for batch motif search in many regions
     * @param m	motif 
     * @param cacheSequence to use sequence caching or not
     */
    public WeightMatrixScorer(WeightMatrix m, boolean cacheSequence) {
    	matrix = m;
        seqgen = new SequenceGenerator();
        seqgen.useCache(cacheSequence);
    }

    /**
     * Constructor that will cache the genome sequence, useful for batch motif search in many regions
     * @param m	motif 
     * @param cacheSequence to use sequence caching or not
     * @param genomePath the file path to the genome dir
     */
    public WeightMatrixScorer(WeightMatrix m, boolean cacheSequence, String genomePath) {
    	matrix = m;
        seqgen = new SequenceGenerator();
        seqgen.useCache(cacheSequence);
        seqgen.setGenomePath(genomePath);
        seqgen.useLocalFiles(true);
    }

    public WeightMatrixScoreProfile execute(Region r) { 
        String seq = seqgen.execute(r);
        seq = seq.toUpperCase();
        double[] fscores = null, rscores = null;

        try { 
        	fscores = score(matrix, seq.toCharArray(), '+');
            seq = SequenceUtils.reverseComplement(seq);
            rscores = score(matrix, seq.toCharArray(), '-');

        } catch (ArrayIndexOutOfBoundsException e) { 
            e.printStackTrace(System.err);
        }
       	
        return new WeightMatrixScoreProfile(matrix, fscores, rscores);
    }
    /**
     * Get the score profile for both strands
     * @param seq
     * @return
     */
    public WeightMatrixScoreProfile execute(String seq) { 
    	seq = seq.toUpperCase();
        double[] fscores = null, rscores = null;
    	fscores = score(matrix, seq.toCharArray(), '+');
        seq = SequenceUtils.reverseComplement(seq);
        rscores = score(matrix, seq.toCharArray(), '-');
        return new WeightMatrixScoreProfile(matrix, fscores, rscores);
    }

    public static double[] score(WeightMatrix matrix, char[] sequence, char strand) {
        double[] results = new double[sequence.length];
        /* scan through the sequence */
        int length = matrix.length();
       	for (int i = 0; i < sequence.length; i++) {
       		results[i] = (float)matrix.getMinScore();
       	}
       	if (sequence.length<length)
     		return results;
       	
        for (int i = 0; i <= sequence.length - length; i++) {
            float score = (float)0.0;
            for (int j = 0; j < length; j++) {
                score += matrix.matrix[j][sequence[i+j]];
            }
            
            if(strand=='-') { 
            	results[sequence.length-length-i] = score;
            } else { 
            	results[i] = score;
            }
        }
        return results;
    }
    /**
     * Score a sequence that is shorter than the matrix<br>
     * find the best scoring sub-matrix
     */
    public static Pair<Float,Integer> scorePartialMatrix (WeightMatrix matrix, char[] sequence, boolean bothStrand) {
    	if (matrix.length()<=sequence.length)
    		return null;
    	
        float maxScore = -10000;
        int maxPos = RC;
        char[] seqRC = sequence.clone();
        SequenceUtils.reverseComplement(seqRC);
//        for (int i=0;i<sequence.length;i++)
//        	seqRC[i] = sequence[sequence.length-1-i];
       	
        for (int j = 0; j <= matrix.length()-sequence.length; j++) {
            float score = 0;
            for (int i = 0; i < sequence.length; i++) {
                score += matrix.matrix[j+i][sequence[i]];
            }
            if (maxScore < score){
            	maxScore = score;
            	maxPos = j;
            }
            if (bothStrand){
                score = 0;
                for (int i = 0; i < seqRC.length; i++) {
                    score += matrix.matrix[j+i][seqRC[i]];
                }
                if (maxScore < score){
                	maxScore = score;
                	maxPos = -j+RC;
                }
            }
        }
        return new Pair<Float,Integer>(maxScore, maxPos);
    }
    
    /**
     * Return the maximum motif score of the input sequence (from both directions or only forward direction)
     * @param matrix
     * @param sequence
     * @return
     */
    public static double getMaxSeqScore(WeightMatrix matrix, String sequence, boolean isForwardOnly){
    	if (sequence.length()<matrix.length())
    		return matrix.getMinScore();
    	
    	double[] scores = score(matrix, sequence.toCharArray(), '+');
    	Pair<Double, TreeSet<Integer>> max = StatUtil.findMax(scores);
    	double maxScore = max.car();
    	if (!isForwardOnly){
	    	scores = score(matrix, SequenceUtils.reverseComplement(sequence).toCharArray(), '-');
	    	max = StatUtil.findMax(scores);
	    	maxScore = Math.max(maxScore, max.car());
    	}
    	return maxScore;
    }
    
    /**
     * Return the highest scoring sequence in the region
     */
    public String getMaxScoreSequence(Region r, double threshold, int extend){
        int length = matrix.length();
        String seq = seqgen.execute(r.expand(0, length));
        seq = seq.toUpperCase();
        String hit=null;

        char[] sequence = seq.toCharArray();
        for (int i = 0; i <= sequence.length - length; i++) {
            float score = (float)0.0;
            for (int j = 0; j < length; j++) {
                score += matrix.matrix[j][sequence[i+j]];
            }
            if (score>threshold){
            	int start = i-extend;
            	if (start<0) continue;
            	int end = i+length-1+extend;
            	if (end>sequence.length - length) continue;
            	hit = seq.substring(start, end);
            	threshold = score;
            }
        }
        seq = SequenceUtils.reverseComplement(seq);
        sequence = seq.toCharArray();
        for (int i = 0; i <= sequence.length - length; i++) {
            float score = (float)0.0;
            for (int j = 0; j < length; j++) {
                score += matrix.matrix[j][sequence[i+j]];
            }
            if (score>threshold){
            	int start = i-extend;
            	if (start<0) continue;
            	int end = i+length-1+extend;
            	if (end>sequence.length - length) continue;
            	hit = seq.substring(start, end);
            	threshold = score;
            }
        }
        
        return hit;
    }
    
    /**
     * Return the highest scoring sequence in the region
     */
    public static String getMaxScoreSequence(WeightMatrix matrix, String seq, double threshold, int extend){
    	String maxStr = WeightMatrix.getMaxLetters(matrix);
        int length = matrix.length();
        seq = seq.toUpperCase();
        String hit=null;

        char[] sequence = seq.toCharArray();
        for (int i = 0; i <= sequence.length - length; i++) {
            float score = (float)0.0;
            for (int j = 0; j < length; j++) {
                score += matrix.matrix[j][sequence[i+j]];
            }
            if (score>threshold){
            	int start = i-extend;
            	if (start<0) 
            		continue;
            	int end = i+length-1+extend;
            	if (end>sequence.length - length) 
            		continue;
            	hit = seq.substring(start, end+1);		// +1 for end exclusive
            	threshold = score;
            }
        }
        seq = SequenceUtils.reverseComplement(seq);
        sequence = seq.toCharArray();
        for (int i = 0; i <= sequence.length - length; i++) {
            float score = (float)0.0;
            for (int j = 0; j < length; j++) {
                score += matrix.matrix[j][sequence[i+j]];
            }
            if (score>threshold){
            	int start = i-extend;
            	if (start<0) 
            		continue;
            	int end = i+length-1+extend;
            	if (end>sequence.length - length) 
            		continue;
            	hit = seq.substring(start, end+1);		// +1 for end exclusive
            	threshold = score;
            }
        }
        
        return hit;
    }

}
