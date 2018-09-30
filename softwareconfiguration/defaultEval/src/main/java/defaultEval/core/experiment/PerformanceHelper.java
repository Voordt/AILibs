package defaultEval.core.experiment;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Properties;

import org.apache.commons.math.stat.descriptive.moment.Mean;
import org.apache.commons.math3.stat.descriptive.moment.StandardDeviation;
import org.apache.commons.math3.stat.descriptive.moment.Variance;

import jaicore.basic.SQLAdapter;
import scala.annotation.elidable;
import scala.runtime.StringFormat;

/**
 * Creates the tables of the performance of each pipeline with each optimization technique
 * 
 * @author Joshua
 *
 */
public class PerformanceHelper {

	public static Properties settings = new Properties();
	
	
	public static void main(String[] args) throws SQLException, FileNotFoundException, IOException {
		settings.load(new FileReader("helper.properties"));
		for(String arg: args) {
			settings.put(arg.split("=")[0], arg.split("=")[1]);
		}
		
		for(String data : readData()) {
			System.out.println("\n\n \\subsection{" + data + "}\n");
			System.out.println("\\begin{longtable}{ l| c | c | c | c | }");
			System.out.println("\\hline{\\tiny}");
			System.out.println("Pipeline & Default & SMAC & Hyperband & ML-Plan \\\\");
			System.out.println("\\hline{\\tiny}");
			new PerformanceHelper().createRanking(data);
			System.out.println("\\end{longtable}");
			
		}
	}
	
	
	
	class ResultEntry implements Comparable<ResultEntry>{
		double performace;
		String classifierName;
		String preprocessorName;
		
		public ResultEntry(double per, String clas, String pre) {
			performace = per;
			preprocessorName = pre;
			classifierName = clas;
		}
		
		@Override
		public int compareTo(ResultEntry o) {
			return Double.compare(performace, o.performace);
		}
		
		@Override
		public String toString() {
			StringBuilder sb = new StringBuilder("[");
			if(checkVerboseLevel(3)) {
				sb.append(preprocessorName);
				sb.append(", ");
				sb.append(classifierName);
				sb.append(", ");
			}
			sb.append(performace);
			sb.append("]");
			return sb.toString();
		}
		
		public boolean isEqualPipeline(ResultEntry e) {
			return classifierName.equals(e.classifierName) && preprocessorName.equals(e.preprocessorName);
		}
		
	}
	
	
	public SQLAdapter adapter;
	
	
	public PerformanceHelper() {
		adapter = new SQLAdapter(settings.getProperty("db.host"), settings.getProperty("db.username"), settings.getProperty("db.password"), settings.getProperty("db.database"));
				
	}
	
	
	
	
	/**
	 * Creates a ranking based on the given Dataset
	 * 
	 * @param dataset
	 * @throws SQLException 
	 */
	private void createRanking(String dataset) throws SQLException {
		
		HashMap<String, HashMap<String, String>> results = new HashMap<>();
		
		HashMap<String, Integer> optimizerResults = new HashMap<>();
		
		for(String classifierName : readClassifiers()) {
			for(String p : readPreprocessors()) {

				
				double bestMean = 1000;
				String bestOptimizer = "";
				
				for(String optimizerName : new String[] {"default", "SMAC", "Hyperband", "ML-PLAN"}) {
					StringBuilder queryStringSB = new StringBuilder();
					queryStringSB.append("SELECT * FROM ");
					queryStringSB.append(settings.getProperty("db.table"));
					queryStringSB.append(" WHERE ");					
					
					queryStringSB.append("dataset");
					queryStringSB.append(" =");
					queryStringSB.append("'");
					queryStringSB.append(dataset);
					queryStringSB.append("'");
					
					queryStringSB.append(" AND ");
					
					queryStringSB.append("classifier");
					queryStringSB.append(" =");
					queryStringSB.append("'");
					queryStringSB.append(classifierName);
					queryStringSB.append("'");
					
					queryStringSB.append(" AND ");
					
					queryStringSB.append("optimizer");
					queryStringSB.append("=");
					queryStringSB.append("'");
					queryStringSB.append(optimizerName);
					queryStringSB.append("'");
					
					queryStringSB.append(" AND ");
					
					queryStringSB.append("preprocessor");
					queryStringSB.append("=");
					queryStringSB.append("'");
					queryStringSB.append(p);
					queryStringSB.append("';");
					
					
					ResultSet rs = this.adapter.getPreparedStatement(queryStringSB.toString()).executeQuery();
					ArrayList<Double> performance = new ArrayList<>();
					int i = 0;
					double m = 0;
					int error = 0;
					while (rs.next()) {
						int seed = -1;
						seed = Integer.valueOf(rs.getString("seed"));
						
						if(seed < 1000 && optimizerName.equals("ML-PLAN")) {
							continue;
						}
						
						if(rs.getString("pctIncorrect") != null && !rs.getString("pctIncorrect").equals("")) {
							performance.add(rs.getDouble("pctIncorrect"));
							i++;
							m += rs.getDouble("pctIncorrect");
						}else {
							error++;
						}
					}
					
					double[] data = new double[i];
					for (int j = 0; j < data.length; j++) {
						data[j] = performance.get(j);
					}
					
					if(i > 0) {
						m /= i;
						
						Variance var = new Variance();
						double varValue = var.evaluate(data);
						
						StandardDeviation stdDev = new StandardDeviation();
						double stdDevValue = stdDev.evaluate(data);
						
						
						HashMap<String, String> r = new HashMap<>();
						
						r.put("number", String.format("%d", i));
						r.put("error", String.format("%d", error));
						r.put("mean", String.format("%.2f", m));
						r.put("std", String.format("%.2f", stdDevValue));
						
						//System.out.print(String.format(" & %d(%d) %.2f$\\pm$%.2f", i, error, m, stdDevValue));
						//System.out.print(String.format("& %d", i));
						
						results.put(classifierName + p + optimizerName, r);
						
						if(bestMean > m) {
							bestMean = m;
							bestOptimizer = optimizerName;
						}
						
					}else {
						//System.out.print(String.format(" & -(%d)", error));
						HashMap<String, String> r = new HashMap<>();
						
						r.put("number", String.format("%d", i));
						r.put("error", String.format("%d", error));
						r.put("mean", String.format("-"));
						r.put("std", String.format("-"));
						
						//System.out.print(String.format(" & %d(%d) %.2f$\\pm$%.2f", i, error, m, stdDevValue));
						//System.out.print(String.format("& %d", i));
						
						results.put(classifierName + p + optimizerName, r);
						
					}
					
					
				}
				
				HashMap<String, String> r = new HashMap<>();
				r.put("bestMean", String.format("%f", bestMean));
				r.put("bestOptimizer", String.format("%s", bestOptimizer));
				results.put(classifierName + p, r);
				
				Integer n = optimizerResults.get(bestOptimizer);
				int nn = (n == null) ? 0 : n;
				nn++;
				optimizerResults.put(bestOptimizer, nn);
			}
		}
		
		for (String opt : optimizerResults.keySet()) {
		//	System.out.println(opt + " " + optimizerResults.get(opt));
		}
		
		
		for(String classifierName : readClassifiers()) {
			for(String p : readPreprocessors()) {

				System.out.print(String.format("%s;%s", getShortName(classifierName), GetPreShortName(p)));
				
				for(String optimizerName : new String[] {"default", "SMAC", "Hyperband", "ML-PLAN"}) {
					HashMap<String, String> r = results.get(classifierName + p + optimizerName);
					
					if(!r.get("number").equals("0")) {
						
						if(results.get(classifierName+p).get("bestOptimizer").equals(optimizerName)) {
							System.out.print(String.format(" & \\textbf{%s(%s) %s$\\pm$%s}", r.get("number"), r.get("error"), r.get("mean"), r.get("std")));
							
						}else {
							System.out.print(String.format(" & %s(%s) %s$\\pm$%s", r.get("number"), r.get("error"), r.get("mean"), r.get("std")));
								
						}
						
						
					}else {
						System.out.print(String.format(" & %s(%s) %s$\\pm$%s", r.get("number"), r.get("error"), "-", "-"));
						
					}
					
				}
				System.out.print(String.format("\\\\ \n"));
			}
		}
		
		
	}
	
	private static String getShortName(String FullName) {
		String[] s = FullName.split("\\."); 
		return s[s.length-1];
	}
	
	private static String GetPreShortName(String PreprocessorName) {
		if(PreprocessorName.equals("null")) {
			return "null";
		}
		String[] p = PreprocessorName.split(";");
		return getShortName(p[0]) + ";" + getShortName(p[1]);
	}
	
	
	
	private static Collection<String> readClassifiers(){
		ArrayList<String> result = new ArrayList<>();
		for (String string : settings.getProperty("classifiers").split(",")) {
			result.add(string.trim());
		}
		return result;
	}
	
	private static Collection<String> readPreprocessors(){
		ArrayList<String> result = new ArrayList<>();
		for (String string : settings.getProperty("preprocessors").split(",")) {
			result.add(string.trim());
		}
		return result;
	}
	
	private static Collection<String> readData(){
		ArrayList<String> result = new ArrayList<>();
		for (String string : settings.getProperty("datasets").split(",")) {
			result.add(string.trim());
		}
		return result;
	}
	
	/**
	 * 1 is most important
	 * 
	 * @param level
	 * @return
	 */
	private static boolean checkVerboseLevel(int level) {
		int v = Integer.valueOf(settings.getProperty("verbose"));
		return level <= v;
	}
	
	
}
