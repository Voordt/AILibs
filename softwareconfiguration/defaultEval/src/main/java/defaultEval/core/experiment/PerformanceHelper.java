package defaultEval.core.experiment;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Properties;

import org.apache.commons.math.stat.descriptive.moment.Mean;
import org.apache.commons.math3.stat.descriptive.moment.StandardDeviation;
import org.apache.commons.math3.stat.descriptive.moment.Variance;

import jaicore.basic.SQLAdapter;
import scala.annotation.elidable;
import scala.runtime.StringFormat;


public class PerformanceHelper {

	public static Properties settings = new Properties();
	
	
	public static void main(String[] args) throws SQLException, FileNotFoundException, IOException {
		settings.load(new FileReader("helper.properties"));
		for(String arg: args) {
			settings.put(arg.split("=")[0], arg.split("=")[1]);
		}
		
		for(String data : readData()) {
			System.out.println("\n\n section{" + data + "}\n");
			System.out.println("\\begin{longtable}{ l|c c c c |c c c c |c c c c | }");
			System.out.println("name & n & e & loss & stddev & n & e & loss & stddev & n & e & loss & stddev \\\\");
			System.out.println("\\hline{\\tiny }");
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
		
		for(String classifierName : readClassifiers()) {
			for(String p : readPreprocessors()) {

				System.out.print(String.format("%s;%s", getShortName(classifierName), GetPreShortName(p)));
				
				for(String optimizerName : new String[] {"default", "SMAC", "Hyperband"}) {
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
						if(rs.getString("pctIncorrect") != null && !rs.getString("pctIncorrect").equals("")) {
							performance.add(rs.getDouble("pctIncorrect")/100d);
							i++;
							m += rs.getDouble("pctIncorrect")/100d;
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
						
						System.out.print(String.format(" & %d", i));
						System.out.print(String.format(" & %d", error));
						System.out.print(String.format(" & %f", m));
						System.out.print(String.format(" & %f", stdDevValue));
						//System.out.print(String.format("& %d", i));
						
					}else {
						System.out.print(String.format(" & %s", "-"));
						System.out.print(String.format(" & %s", "-"));
						System.out.print(String.format(" & %s", "-"));
						System.out.print(String.format(" & %s", "-"));
						
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
