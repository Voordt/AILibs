package defaultEval.core;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintStream;
import java.util.List;
import java.util.Random;

import org.json.JSONObject;

import de.upb.crc901.mlplan.multiclass.DefaultPreorder;
import de.upb.crc901.mlplan.multiclass.MLPlan;
import hasco.model.Component;
import hasco.serialization.ComponentLoader;
import jaicore.ml.WekaUtil;
import weka.core.Instances;


/**
 * Uses ML Plan to optimize hyperparameters
 * 
 * @author Joshua
 *
 */
public class MlPlanOptimizer extends Optimizer {

	public MlPlanOptimizer(Component searcher, Component evaluator, Component classifier, String dataSet,
			File environment, File dataSetFolder, int seed, int maxRuntimeParam, int maxRuntime) {
		super(searcher, evaluator, classifier, dataSet, environment, dataSetFolder, seed, maxRuntimeParam, maxRuntime);
	}

	private MLPlan mlplan;
	
	public JSONObject getJsonNode(File f) throws FileNotFoundException, IOException {
		StringBuilder stringDescriptionSB = new StringBuilder();
	    String line;
	    try (BufferedReader br = new BufferedReader(new FileReader(f))) {
	      while ((line = br.readLine()) != null) {
	        stringDescriptionSB.append(line + "\n");
	      }
	    }
	    String jsonDescription = stringDescriptionSB.toString();
	    jsonDescription = jsonDescription.replaceAll("/\\*(.*)\\*/", "");

	    return new JSONObject(jsonDescription);
	}
	
	@Override
	public void optimize() {		
		try {
			
			File f = new File(environment.getAbsolutePath() + "/models/opt_" + buildFileName() + ".json");
			
			PrintStream modelFile = new PrintStream(new FileOutputStream(f));
			modelFile.println("{");
			modelFile.println("	\"repository\" : \"WEKA - Auto-WEKA Searchspace\",");
			modelFile.println("	\"include\": [\"./weka-classifiers.json\", \"./weka-preprocessors.json\"],");
			modelFile.println("	\"components\" : [ {");
			if(evaluator != null) {
				modelFile.println("	  	    \"name\" : \"pipeline\",");
				modelFile.println("	  	    \"providedInterface\" : [ \"MLPipeline\", \"AbstractClassifier\" ],");
				modelFile.println("	  	    \"requiredInterface\" : [");
				modelFile.println("	  	    	{");
				modelFile.println("	  	    		\"id\": \"preprocessor\",");
				modelFile.println("	  	    		\"name\": \"AbstractPreprocessor\"");
				modelFile.println("	  	    	}, { ");
				modelFile.println("	  	    		\"id\": \"classifier\",");
				modelFile.println("	  	    		\"name\": \"" + classifier.getName() + "\"");
				modelFile.println("	  	    	}");
				modelFile.println("			],");
				modelFile.println("	    	\"parameter\" : [ ]");
				modelFile.println("	    }, {");
				modelFile.println("			\"name\" : \"weka.attributeSelection.AttributeSelection\",");
				modelFile.println("			\"providedInterface\" : [ \"AbstractPreprocessor\" ],");
				modelFile.println("			\"requiredInterface\" : [ {\"id\": \"eval\", \"name\": \""+evaluator.getName()+"\" }, {\"id\": \"search\", \"name\": \""+searcher.getName()+"\" } ],");
				modelFile.println("			\"parameter\" : [ ],");
				modelFile.println("			\"dependencies\" : [ ]");
				modelFile.println("		}");
				
			}else {
				modelFile.println("	  	    \"name\" : \"pipelineWrapper\",");
				modelFile.println("	  	    \"providedInterface\" : [ \"MLPipeline\", \"AbstractClassifier\" ],");
				modelFile.println("	  	    \"requiredInterface\" : [");
				modelFile.println("				{ ");
				modelFile.println("	  	    		\"id\": \"classifier\",");
				modelFile.println("	  	    		\"name\": \""+classifier.getName()+"\"");
				modelFile.println("	  	    	}");
				modelFile.println("			],");
				modelFile.println("	    	\"parameter\" : [ ]");
				modelFile.println("	    }");
				
			}
			modelFile.println("]}");
			
			modelFile.flush();
			modelFile.close();
			
			Instances data = new Instances(new BufferedReader(new FileReader(dataSetFolder.getAbsolutePath() +"/"+ dataSet + ".arff")));
			data.setClassIndex(data.numAttributes() - 1);
			List<Instances> split = WekaUtil.getStratifiedSplit(data, new Random(seed), .7f);

			mlplan = new MLPlan(f);
			
			mlplan.setLoggerName("mlplan");
			mlplan.setTimeout(maxRuntimeParam);
			mlplan.setPortionOfDataForPhase2(.3f);
			mlplan.setNodeEvaluator(new DefaultPreorder());
			mlplan.buildClassifier(split.get(0));
			
			System.out.println(mlplan.getSelectedModel().getComponentInstance().getPrettyPrint());
			
			finalClassifier = mlplan.getSelectedModel().getComponentInstance().getSatisfactionOfRequiredInterfaces().get("classifier");
			if(evaluator != null) {
				finalEvaluator = mlplan.getSelectedModel().getComponentInstance().getSatisfactionOfRequiredInterfaces().get("preprocessor").getSatisfactionOfRequiredInterfaces().get("eval");
				finalSearcher = mlplan.getSelectedModel().getComponentInstance().getSatisfactionOfRequiredInterfaces().get("preprocessor").getSatisfactionOfRequiredInterfaces().get("search");
			}
			
			System.out.println("final-searcher: " + finalSearcher);
			System.out.println("final-evaluator: " + finalEvaluator);
			System.out.println("final-classifier: " + finalClassifier);
			
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	
	@Override
	protected String buildFileName() {
		return super.buildFileName() + "MLPlan";
	}

	public static void main(String[] args) throws Exception {

		ComponentLoader cl_p = new ComponentLoader();
		ComponentLoader cl_c = new ComponentLoader();

		try {
			Util.loadClassifierComponents(cl_c, "F:\\Data\\Uni\\PG\\DefaultEvalEnvironment");
			Util.loadPreprocessorComponents(cl_p, "F:\\Data\\Uni\\PG\\DefaultEvalEnvironment");
		} catch (IOException e1) {
			e1.printStackTrace();
		}
		Component searcher = null;
		Component evaluator = null;
		Component classifier = null;

		for (Component c : cl_p.getComponents()) {
			if (c.getName().equals("weka.attributeSelection.Ranker")) {
				searcher = c;
			}
		}

		for (Component c : cl_p.getComponents()) {
			if (c.getName().equals("weka.attributeSelection.ReliefFAttributeEval")) {
				evaluator = c;
			}
		}
		for (Component c : cl_c.getComponents()) {
			if (c.getName().equals("weka.classifiers.lazy.KStar")) {
				classifier = c;
			}
		}

		MlPlanOptimizer o = new MlPlanOptimizer(searcher, evaluator, classifier, "breast-cancer",
				new File("F:\\Data\\Uni\\PG\\DefaultEvalEnvironment"),
				new File("F:\\Data\\Uni\\PG\\DefaultEvalEnvironment\\datasets"), 0, 10, 50);
		o.optimize();
		
		
		o.getOptimizedClassifier();
		
	}
}
