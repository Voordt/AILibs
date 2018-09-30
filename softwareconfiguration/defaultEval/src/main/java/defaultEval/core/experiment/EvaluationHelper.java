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

import org.apache.commons.math.stat.descriptive.moment.StandardDeviation;

import jaicore.basic.SQLAdapter;
import scala.util.parsing.combinator.testing.Str;

/**
 * Helper Class to generate some statistics for gathered data. The Results are printed into System.out
 * 
 * @author Joshua
 *
 */
public class EvaluationHelper {

	public static Properties settings = new Properties();

	public static void main(String[] args) throws SQLException, FileNotFoundException, IOException {
		settings.load(new FileReader("helper.properties"));
		for (String arg : args) {
			settings.put(arg.split("=")[0], arg.split("=")[1]);
		}

		EvaluationHelper inst = new EvaluationHelper();
		for (String data : readData()) {
			inst.createRanking(data);
		}
		inst.createGlobalStatistics();
	}

	/**
	 * Stores the Results gathered from the database
	 * 
	 * @author Joshua
	 */
	class ResultEntry implements Comparable<ResultEntry> {
		double errorRate;
		String classifierName;
		String preprocessorName;
		double distance;
		String optimizerName;
		
		public ResultEntry(double per, String clas, String pre, String opt) {
			errorRate = per;
			preprocessorName = pre;
			classifierName = clas;
			optimizerName = opt;
		}

		@Override
		public int compareTo(ResultEntry o) {
			return Double.compare(errorRate, o.errorRate);
		}

		public String getSimpleClassifierName() {
			String[] a = classifierName.split("\\.");
			return a[a.length - 1];
		}

		public String getSimpleSearcherName() {
			if (preprocessorName.equals("null"))
				return "";
			String[] a = preprocessorName.split("\\;")[0].split("\\.");
			return a[a.length - 1];
		}

		public String getSimpleEvaluatorName() {
			if (preprocessorName.equals("null"))
				return "";
			String[] a = preprocessorName.split("\\;")[1].split("\\.");
			return a[a.length - 1];
		}

		public void setDistance(double distance) {
			this.distance = distance;
		}

		public double getDistance() {
			return distance;
		}

		@Override
		public String toString() {
			StringBuilder sb = new StringBuilder("[");
			if (checkVerboseLevel(3)) {
				sb.append(preprocessorName);
				sb.append(", ");
				sb.append(classifierName);
				sb.append(", ");
			}
			sb.append(errorRate);
			sb.append("]");
			return sb.toString();
		}

		public boolean isEqualPipeline(ResultEntry e) {
			return classifierName.equals(e.classifierName) && preprocessorName.equals(e.preprocessorName);
		}

	}

	public SQLAdapter adapter;

	public EvaluationHelper() {
		adapter = new SQLAdapter(settings.getProperty("db.host"), settings.getProperty("db.username"),
				settings.getProperty("db.password"), settings.getProperty("db.database"));

	}

	HashMap<String, ArrayList<ResultEntry>> resultsDefaultMapping = new HashMap<>();
	HashMap<String, ArrayList<ResultEntry>> resultsOptimizedMapping = new HashMap<>();

	/**
	 * Creates a ranking based on the given Dataset
	 * 
	 * @param dataset
	 * @throws SQLException
	 */
	private void createRanking(String dataset) throws SQLException {
		ArrayList<ResultEntry> resultsDefault = new ArrayList<>();
		ArrayList<ResultEntry> resultsOptimized = new ArrayList<>();

		for (String classifierName : readClassifiers()) {
			for (String p : readPreprocessors()) {

				for (String optimizerName : new String[] { "default", "SMAC", "Hyperband", "ML-PLAN" }) {
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
					double errorRate = 0;
					int i = 0;
					while (rs.next()) {
						int seed = -1;
						seed = Integer.valueOf(rs.getString("seed"));
						if (seed < 1000 && optimizerName.equals("ML-PLAN")) {
							continue; // only use new data from Ml plan which uses the seeds 1001-1015
						}

						if (rs.getString("pctIncorrect") != null && !rs.getString("pctIncorrect").equals("")) {
							errorRate += rs.getDouble("pctIncorrect");
							i++;
						}
					}

					if (i > 5) {
						errorRate /= i;
						switch (optimizerName) {
						case "default":
							resultsDefault.add(new ResultEntry(errorRate, classifierName, p, optimizerName));
							//break;

						default:
							ResultEntry old = null;
							for (ResultEntry resultEntry : resultsOptimized) {
								if (resultEntry.classifierName.equals(classifierName)
										&& resultEntry.preprocessorName.equals(p)) {
									old = resultEntry;
									break;
								}
							}

							if (old != null) {
								if (old.errorRate > errorRate) {
									resultsOptimized.remove(old);
									resultsOptimized.add(new ResultEntry(errorRate, classifierName, p, optimizerName));
								}
							} else {
								resultsOptimized.add(new ResultEntry(errorRate, classifierName, p, optimizerName));
							}

							break;
						}
					} else {
						if (checkVerboseLevel(5)) {
							 System.out.println("No Data found for: " + p + ", " + classifierName + ", " + optimizerName);
						}
					}
				}
			}
		}

		// make sure in both lists are the same pipelines
		resultsDefault.removeIf((e) -> {
			for (ResultEntry resultEntry : resultsOptimized) {
				if (resultEntry.isEqualPipeline(e)) {
					return false;
				}
			}
			return true;
		});
		resultsOptimized.removeIf((e) -> {
			for (ResultEntry resultEntry : resultsDefault) {
				if (resultEntry.isEqualPipeline(e)) {
					return false;
				}
			}
			return true;
		});

		// sort
		Collections.sort(resultsDefault);
		Collections.sort(resultsOptimized);

		System.out.println("RESULTS FOR: '" + dataset + "'");
		System.out.println();

		System.out.println("DEFAULT RESULTS: " + resultsDefault.size());
		System.out.println(resultsDefault);
		System.out.println("OPTIMIZED RESULTS: " + resultsOptimized.size());
		System.out.println(resultsOptimized);

		CompareFirstK(resultsDefault, resultsOptimized);
		System.out.println();

		FindGoodPipelineFromOneRankingInTheOther(resultsDefault, resultsOptimized);

		System.out.println();
		System.out.println();

		CreateLaTexRankingTable(resultsDefault, resultsOptimized);
		System.out.println();

		CalculateDistances(resultsDefault, resultsOptimized);

		System.out.println();
		System.out.println();

		resultsDefaultMapping.put(dataset, resultsDefault);
		resultsOptimizedMapping.put(dataset, resultsOptimized);
	}

	private void CalculateDistances(ArrayList<ResultEntry> resultsDefault, ArrayList<ResultEntry> resultsOptimized) {
		//
		double dist_mean = 0;
		StandardDeviation dist_stdDev = new StandardDeviation();
		for (int j = 0; j < resultsDefault.size(); j++) {
			for (int i = 0; i < resultsOptimized.size(); i++) {
				if (resultsDefault.get(j).isEqualPipeline(resultsOptimized.get(i))) {
					double d = Math.abs(i - j);
					dist_mean += d / (double) resultsDefault.size();
					dist_stdDev.increment(d);
					resultsOptimized.get(i).distance = d;
					resultsDefault.get(j).distance = d;
				}
			}
		}

		System.out.println("Mean Distance: " + dist_mean);
		System.out.println("Distance StdDev: " + dist_stdDev.getResult());
	}

	private void CreateLaTexRankingTable(ArrayList<ResultEntry> resultsDefault,
			ArrayList<ResultEntry> resultsOptimized) {
		for (int i = 0; i < 5; i++) {
			System.out.print((i + 1) + " & ");

			System.out.print(
					resultsDefault.get(i).getSimpleSearcherName() + "-" + resultsDefault.get(i).getSimpleEvaluatorName()
							+ "-" + resultsDefault.get(i).getSimpleClassifierName());
			System.out.print(" & ");
			System.out.print(resultsOptimized.get(i).getSimpleSearcherName() + "-"
					+ resultsOptimized.get(i).getSimpleEvaluatorName() + "-"
					+ resultsOptimized.get(i).getSimpleClassifierName());
			System.out.print(" \\\\ \n");

		}
	}

	private void FindGoodPipelineFromOneRankingInTheOther(ArrayList<ResultEntry> resultsDefault,
			ArrayList<ResultEntry> resultsOptimized) {
		// what would be the result if k would have been k'=m
		int m_max = Math.min(Integer.valueOf(settings.getProperty("m", "20")), resultsDefault.size());

		for (int m = 0; m < m_max; m++) {
			for (int j = 0; j < resultsOptimized.size(); j++) {
				if (resultsDefault.get(m).isEqualPipeline(resultsOptimized.get(j))) {
					System.out.println("Default index " + m + " is on index " + j + " with optimized config");
				}
			}
		}

		System.out.println();

		// best performances
		System.out.println("Best: " + resultsOptimized.get(0).errorRate);

		// k = 5
		ResultEntry best = null;
		for (int j = 0; j < 5; j++) {
			for (int i = 0; i < resultsOptimized.size(); i++) {
				if (resultsDefault.get(j).isEqualPipeline(resultsOptimized.get(i))) {
					if (best == null || best.errorRate > resultsOptimized.get(i).errorRate) {
						best = resultsOptimized.get(i);
					}
				}
			}
		}
		System.out.println("Best k=5: " + best.errorRate);

		// k= 10
		best = null;
		for (int j = 0; j < 10; j++) {
			for (int i = 0; i < resultsOptimized.size(); i++) {
				if (resultsDefault.get(j).isEqualPipeline(resultsOptimized.get(i))) {
					if (best == null || best.errorRate > resultsOptimized.get(i).errorRate) {
						best = resultsOptimized.get(i);
					}
				}
			}
		}
		System.out.println("Best k=10: " + best.errorRate);
	}

	private void CompareFirstK(ArrayList<ResultEntry> resultsDefault, ArrayList<ResultEntry> resultsOptimized) {
		// compare the first k
		int k_max = Math.min(Integer.valueOf(settings.getProperty("k", "20")), resultsDefault.size());

		for (int k = 1; k <= k_max; k++) {
			int n = 0;

			for (int i = 0; i < k; i++) {
				for (int j = 0; j < k; j++) {
					if (resultsDefault.get(i).isEqualPipeline(resultsOptimized.get(j))) {
						n++;
					}
				}
			}
			System.out.println(n + " pipeline(s) are/is in the top k=" + k + " of both lists.");
		}
		System.out.println();

		// where are the top l from optimize in default
		int l_max = Math.min(Integer.valueOf(settings.getProperty("l", "20")), resultsDefault.size());

		for (int l = 0; l < l_max; l++) {
			for (int i = 0; i < resultsDefault.size(); i++) {
				if (resultsDefault.get(i).isEqualPipeline(resultsOptimized.get(l))) {
					System.out.println("Optimized index " + l + " is on index " + i + " with default config");
				}
			}
		}
	}

	private void createGlobalStatistics() {
		System.out.println();
		System.out.println();

		// mean of pipeline distance
		System.out.println("Mean of pipeline Distance");

		for (String classifierName : readClassifiers()) {
			for (String preprocessorName : readPreprocessors()) {

				// gather all pipeline Distances for this pipeline
				double d = 0;
				int n = 0;
				StandardDeviation std = new StandardDeviation();
				
				for (ArrayList<ResultEntry> result : resultsOptimizedMapping.values()) {
					for (int j = 0; j < result.size(); j++) {
						if (result.get(j).classifierName.equals(classifierName)	&& result.get(j).preprocessorName.equals(preprocessorName)) {
							d += result.get(j).distance;
							std.increment(result.get(j).distance);
							n++;
						}
					}
				}
				
				System.out.println(getSimpleClassifierName(classifierName) + "-" + getSimpleSearcherName(preprocessorName) + "-" + getSimpleEvaluatorName(preprocessorName) + " & " + String.format("%.2f", (d/(double)n)) + "$\\pm$" + String.format("%.2f", std.getResult()) + " \\\\");
				
			}
		}
		
		System.out.println();
		
		// Optimizer Stats
		for (String optimizerName : new String[] { "default", "SMAC", "Hyperband", "ML-PLAN" }) {
			int n = 0;
			
			for (String key : resultsOptimizedMapping.keySet()) {
				for (int j = 0; j < resultsOptimizedMapping.get(key).size(); j++) {
					if(resultsOptimizedMapping.get(key).get(j).optimizerName.equals(optimizerName)) {
						n++;		
					}
				}
			}
			System.out.println(optimizerName + " best result " + n);
		}
	}

	
	// -------------------- HELPER -------------------------
	
	private static Collection<String> readClassifiers() {
		ArrayList<String> result = new ArrayList<>();
		for (String string : settings.getProperty("classifiers").split(",")) {
			result.add(string.trim());
		}
		return result;
	}

	private static Collection<String> readPreprocessors() {
		ArrayList<String> result = new ArrayList<>();
		for (String string : settings.getProperty("preprocessors").split(",")) {
			result.add(string.trim());
		}
		return result;
	}

	private static Collection<String> readData() {
		ArrayList<String> result = new ArrayList<>();
		for (String string : settings.getProperty("datasets").split(",")) {
			result.add(string.trim());
		}
		return result;
	}
	
	
	public static String getSimpleClassifierName(String classifierName) {
		String[] a = classifierName.split("\\.");
		return a[a.length - 1];
	}

	public static String getSimpleSearcherName(String preprocessorName) {
		if (preprocessorName.equals("null"))
			return "";
		String[] a = preprocessorName.split("\\;")[0].split("\\.");
		return a[a.length - 1];
	}

	public static String getSimpleEvaluatorName(String preprocessorName) {
		if (preprocessorName.equals("null"))
			return "";
		String[] a = preprocessorName.split("\\;")[1].split("\\.");
		return a[a.length - 1];
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
