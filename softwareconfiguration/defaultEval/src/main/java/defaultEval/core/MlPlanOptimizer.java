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
import jaicore.planning.graphgenerators.task.tfd.TFDNode;
import jaicore.search.algorithms.standard.core.INodeEvaluator;
import jaicore.search.structure.core.Node;
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
	
	
	@Override
	public void optimize() {		
		try {
			
			// Create Component file to restrict HASCO to a single Pipeline
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

			
			// start ML PLan
			mlplan = new MLPlan(f);
			
			mlplan.setLoggerName("mlplan");
			mlplan.setTimeout(maxRuntimeParam);
			mlplan.setPortionOfDataForPhase2(.3f);
//			mlplan.setNodeEvaluator(new DefaultPreorder());
			mlplan.setNodeEvaluator(new INodeEvaluator<TFDNode, Double>() {
				@Override
				public Double f(Node<TFDNode, ?> node) throws Throwable {
					return null;
				}
			});
			
			
			mlplan.buildClassifier(split.get(0));
			
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

}
