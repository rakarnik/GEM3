package edu.mit.csail.cgs.ewok.verbs.chipseq;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.Vector;

import edu.mit.csail.cgs.datasets.general.Region;
import edu.mit.csail.cgs.datasets.species.Genome;
import edu.mit.csail.cgs.datasets.species.Organism;
import edu.mit.csail.cgs.tools.utils.Args;
import edu.mit.csail.cgs.utils.ArgParser;
import edu.mit.csail.cgs.utils.NotFoundException;
import edu.mit.csail.cgs.utils.Pair;

/* 
 * Class to parse out put from GPS Chip-Seq binding peak finder
 */
public class GPSParser {
	
	public static void main(String[] args) {
		Genome genome=null;
		ArgParser ap = new ArgParser(args);
	    try {
	      Pair<Organism, Genome> pair = Args.parseGenome(args);
	      if(pair==null){
	        //Make fake genome... chr lengths provided???
	        if(ap.hasKey("geninfo")){
	          genome = new Genome("Genome", new File(ap.getKeyValue("geninfo")));
	            }else{
	              System.err.println("No genome provided; provide a Gifford lab DB genome name or a file containing chromosome name/length pairs.");;System.exit(1);
	            }
	      }else{
	        genome = pair.cdr();
	      }
	    } catch (NotFoundException e) {
	      e.printStackTrace();
	    }
	    String filename = Args.parseString(args,"GPS",null); 
	    List<GPSPeak> peaks = parseGPSOutput(filename, genome);
	    System.out.println(peaks.size());
	    System.out.println(GPSPeak.toGPS_Header());
	    System.out.println(peaks.get(0).toGPS());
	    System.out.println(GPSPeak.toGPS_short_Header());
	    System.out.println(peaks.get(0).toGPS_short());
	}
	/**
	 * Parses data in the GPS output format
	 * @param filename name of the file containing the data
	 * @return a List of hit objects
	 */
	public static List<GPSPeak> parseGPSOutput(String filename, Genome g) {
		ArrayList<GPSPeak> results = new ArrayList<GPSPeak>();

		FileReader in = null;
		BufferedReader bin = null;
		int count = 0;
		try {
			in = new FileReader(filename);
			bin = new BufferedReader(in);
			
			String line;
			while((line = bin.readLine()) != null) { 
//			  if (count % 1000 == 0) {
//			    if (count % 10000 == 0) {
//			      System.out.println(count);
//			    }
//			    else {
//			      System.out.print(count);
//			    }
//			  }
//			  else if (count % 100 == 0) {
//			    System.out.print(".");
//			  }
				line = line.trim();
	            String[] f=line.split("\t");
	            if (f[0].equals("Position")||f[0].equals("chr")){
	            	continue;
	            }
				GPSPeak hit = GPSParser.parseLine(g, line, 0);
				if (hit!=null) {
					results.add(hit);
			  }
				
				count++;
			}			
			System.out.println();
		}
		catch(IOException ioex) {
			//logger.error("Error parsing file", ioex);
		}
		finally {
			try {
				if (bin != null) {
					bin.close();
				}
			}
			catch(IOException ioex2) {
				//nothing left to do here, just log the error
				//logger.error("Error closing buffered reader", ioex2);
			}			
		}
		return results;
	}



	/**
	 * Parse a single line of text into a hit object
	 * 
	 * old format
	 * Position	Rank_Sum	Strength	EM_Posi	Shape	Shape_Z	Shape_Param	ShapeAsymmetry	IpStrength	CtrlStrength	Q_value_log10	MixingProb	NearestGene	Distance	
	 * 8:20401711	47581	200.0	 8:20401711	1.30	1.6223	7.454790	0.33			200.0		4.8				47.53			0.9745		Hoxa1		2147483647	
	   New format
	   0			1			2		3				4				5				6			7			8			9
	   Position		IpStrength	Shape	CtrlStrength	Q_value_log10	P_value_log10	UnaryEvent	NearestGene	Distance	Alpha
	   18:75725340	230.5		-0.13	0.4				62.51			67.17			1			NONE		2147483647	9.0	

	 * @param gpsLine a line of text representing a hit
	 * @return a hit object containing the data from the specified line
	 */
	private static GPSPeak parseLine(Genome g, String gpsLine, int lineNumber) {
		GPSPeak peak;
		String[] t = gpsLine.split("\t");
	if (t.length < 12) {
      try { 
      Region r = Region.fromString(g, t[0]);
			peak = new GPSPeak(g, r.getChrom(), r.getStart(), 
					Double.parseDouble(t[1]), Double.parseDouble(t[3]), Double.parseDouble(t[4]), 
					Double.parseDouble(t[5]), Double.parseDouble(t[2]), Integer.parseInt(t[6]), t[7], Integer.parseInt(t[8]));
      }
      catch (Exception ex) {
        //logger.error("Parse error on line " + lineNumber + ".", ex);
        return null;
      }
    }
	else if (t.length == 12) {
// GPS output format 2010-07-31		
//	Position	   IP	Control	IP/Ctrl	Q_-lg10	P_-lg10	  Shape	
//	Joint	NearestGene	Distance	Alpha	EM_Position
	      try { 
	    	  Region r = Region.fromString(g, t[0]);
				peak = new GPSPeak(g, r.getChrom(), r.getStart(), 
						Double.parseDouble(t[1]), Double.parseDouble(t[2]), Double.parseDouble(t[4]), 
						Double.parseDouble(t[5]), Double.parseDouble(t[6]), Integer.parseInt(t[7]), t[8], Integer.parseInt(t[9]));
	      }
	      catch (Exception ex) {
	        //logger.error("Parse error on line " + lineNumber + ".", ex);
	        return null;
	      }
	    }
	else if (t.length == 13) {
	      try { 
	      Region r = Region.fromString(g, t[0]);
				peak = new GPSPeak(g, r.getChrom(), r.getStart(), 
						Double.parseDouble(t[2]), Double.parseDouble(t[3]), Double.parseDouble(t[5]), 
						Double.parseDouble(t[6]), Double.parseDouble(t[1]), Integer.parseInt(t[7]), t[8], Integer.parseInt(t[9]));
	      }
	      catch (Exception ex) {
	        //logger.error("Parse error on line " + lineNumber + ".", ex);
	        return null;
	      }
	    }
	else if (t.length == 14) {
      try { 
        Region r = Region.fromString(g, t[0]);
        Region em_pos = Region.fromString(g, t[3]);
//        GPSPeak(Genome g, String chr, int pos, int EM_pos, double strength, 
//            double controlStrength, double qvalue, double shape, double shapeZ)
        peak = new GPSPeak(g, r.getChrom(), r.getStart(), em_pos.getStart(),
            Double.parseDouble(t[2]), Double.parseDouble(t[9]), Double.parseDouble(t[10]),
            Double.parseDouble(t[4]), Double.parseDouble(t[5]), Double.parseDouble(t[11]), t[12], Integer.parseInt(t[13]));
      }
      catch (Exception ex) {
        //logger.error("Parse error on line " + lineNumber + ".", ex);
        return null;
      }
    }
    else if (t.length == 15) {
			try { 
				Region r = Region.fromString(g, t[0]);
				Region em_pos = Region.fromString(g, t[3]);
//				GPSPeak(Genome g, String chr, int pos, int EM_pos, double strength, 
//						double controlStrength, double qvalue, double shape, double shapeZ)
				peak = new GPSPeak(g, r.getChrom(), r.getStart(), em_pos.getStart(),
						Double.parseDouble(t[2]), Double.parseDouble(t[9]), Double.parseDouble(t[10]), Double.parseDouble(t[11]),
						Double.parseDouble(t[4]), Double.parseDouble(t[5]), Double.parseDouble(t[12]), t[13], Integer.parseInt(t[14]));
			}
			catch (Exception ex) {
				//logger.error("Parse error on line " + lineNumber + ".", ex);
				return null;
			}
		}
    else if (t.length == 16) {
		try { 
			Region r = Region.fromString(g, t[0]);
//			GPSPeak(Genome g, String chr, int pos, int EM_pos, double strength, 
//					double controlStrength, double qvalue, double shape, double shapeZ)		
			peak = new GPSPeak(g, r.getChrom(), r.getStart(), 
					Double.parseDouble(t[3+0]), 0.0, 0.0, 
					0.0, Double.parseDouble(t[10]), Integer.parseInt(t[10]), t[11], Integer.parseInt(t[12]));
		}
				
		catch (Exception ex) {
			//logger.error("Parse error on line " + lineNumber + ".", ex);
			return null;
		}
	}
	
    else if (t.length == 17) {
		try { 
			Region r = Region.fromString(g, t[0]);
//			GPSPeak(Genome g, String chr, int pos, int EM_pos, double strength, 
//					double controlStrength, double qvalue, double shape, double shapeZ)		
			peak = new GPSPeak(g, r.getChrom(), r.getStart(), 
					Double.parseDouble(t[3+0]), Double.parseDouble(t[3+1]), Double.parseDouble(t[3+2]), 
					Double.parseDouble(t[3+3]), Double.parseDouble(t[2]), Integer.parseInt(t[11]), t[11+1], Integer.parseInt(t[11+2]));
		}
		catch (Exception ex) {
			//logger.error("Parse error on line " + lineNumber + ".", ex);
			return null;
		}
    }
		else {
			//logger.error("Line " + lineNumber + " has " + tokens.length + " tokens.");
			return null;
		}
		return peak;
	}
}
