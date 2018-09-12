package defaultEval.core;

import java.io.File;
import java.util.ArrayList;

import org.apache.commons.math3.util.Pair;

import de.upb.crc901.automl.hascowekaml.WEKAPipelineFactory;
import de.upb.crc901.automl.pipeline.basic.MLPipeline;
import defaultEval.core.Util.ParamType;
import hasco.model.Component;
import hasco.model.ComponentInstance;
import hasco.model.NumericParameterDomain;
import hasco.model.Parameter;
import hasco.model.ParameterDomain;
import weka.classifiers.Classifier;


/**
 * Abstract hyperparameter optimizer for a given pipeline and dataset.
 * 
 * @author Joshua
 *
 */
public abstract class Optimizer {

	/** Searcher of the Pipeline @see {@link MLPipeline}*/
	protected Component searcher;
	
	/** Evaluator of the Pipeline @see {@link MLPipeline}*/
	protected Component evaluator;
	
	/** Classifier of the Pipeline @see {@link MLPipeline}*/
	protected Component classifier;
	
	/** The name of the  dataset to be used. Must be located in the dataSetFolder. */
	protected String dataSet;
	
	/** List of parameters for the entire pipeline */
	protected ArrayList<Pair<Parameter, ParamType>> parameterList = new ArrayList<>();
	
	/** Location on disk to store tmp files */
	protected File environment;
	/** Location on disk to find datasets */
	protected File dataSetFolder;
	
	/** Random seed */
	int seed = 0;
	
	/** Timeout parameter for external programs */
	protected int maxRuntimeParam;
	/** Timeout to kill external processes if they dont follow maxRuntimeParam */
	protected int maxRuntime;
	
	/** Output Searcher */
	protected ComponentInstance finalSearcher;
	/** Output Evaluator */
	protected ComponentInstance finalEvaluator;
	/** Output Classifier */
	protected ComponentInstance finalClassifier;
	
	
	/**
	 * 
	 * @param searcher - Searcher Component
	 * @param evaluator - Evaluator Component
	 * @param classifier - Classifier Component
	 * @param dataSet - Name of the dataset
	 * @param environment - Directory to store data
	 * @param dataSetFolder - Directory to find datasets
	 * @param seed - Random seed
	 * @param maxRuntimeParam - Timeout for external processes (parameter)
	 * @param maxRuntime - Timeout for external processes (process will be killed after this time)
	 */
	public Optimizer(Component searcher, Component evaluator, Component classifier, String dataSet, File environment, File dataSetFolder, int seed, int maxRuntimeParam, int maxRuntime) {
		this.searcher = searcher;
		this.evaluator = evaluator;
		
		this.classifier = classifier;
		
		this.dataSet = dataSet;
		
		if(searcher != null) {
			for (Parameter p : searcher.getParameters()) {
				parameterList.add(new Pair<>(p, ParamType.searcher));
			}
			for (Parameter p : evaluator.getParameters()) {
				parameterList.add(new Pair<>(p, ParamType.evaluator));
			}
		}
		
		for (Parameter p : classifier.getParameters()) {
			parameterList.add(new Pair<>(p, ParamType.classifier));
		}
		
		this.environment = environment;
		this.dataSetFolder = dataSetFolder;
		this.seed = seed;
		this.maxRuntimeParam = maxRuntimeParam;
		this.maxRuntime = maxRuntime;
		
	}

	/**
	 * actual optimization happens here
	 */
	public abstract void optimize();

	
	/**
	 * unique name for a given configuration (searcher,evaluator,classifier,dataset,seed)
	 * Must be overwritten to be unique for different optimizers!
	 * 
	 * @return unique name
	 */
	protected String buildFileName() {
		StringBuilder sb = new StringBuilder((searcher != null) ? (searcher.getName()+"_"+evaluator.getName()) : "null");
		sb.append("_");
		sb.append(classifier.getName());
		sb.append("_");
		sb.append(dataSet);
		sb.append("_");
		sb.append(seed);
		return sb.toString().replaceAll("\\.", "").replaceAll("-", "_");
	}
	
	/**
	 * 
	 * @param str - input string containing an double
	 * @return - double of that string casted to int
	 */
	public int getDoubleStringAsInt(String str) {
		return (int)Double.valueOf(str).doubleValue();
	}
	
	/**
	 * some optimizers uses only doubles for numeric values in output. This converts them to ints
	 * 
	 * @param input
	 * @param pd
	 * @return
	 */
	public String correctParameterSyntax(String input, ParameterDomain pd) {
		if(pd instanceof NumericParameterDomain) {
			NumericParameterDomain d = (NumericParameterDomain) pd;
			if(d.isInteger()) {
				return Integer.toString(getDoubleStringAsInt(input));
			}
		}
		return input;
	}
	
	@Deprecated
	protected String getUniqueParamName(Parameter p, ParamType t) {
		return Util.convertToUniqueParamName(p.getName(), t);
	}
	
	@Deprecated
	protected String getUniqueParamName(Pair<Parameter, ParamType> p) {
		return Util.convertToUniqueParamName(p.getFirst().getName(), p.getSecond());
	}
	
	
	public ComponentInstance getFinalClassifier() {
		return finalClassifier;
	}
	
	public ComponentInstance getFinalEvaluator() {
		return finalEvaluator;
	}
	
	public ComponentInstance getFinalSearcher() {
		return finalSearcher;
	}
	
	/**
	 * Creates a Pipeline Classifier for final results
	 * 
	 * @return - the classifier
	 * @throws Exception
	 */
	public Classifier getOptimizedClassifier() throws Exception {
		WEKAPipelineFactory factory = new WEKAPipelineFactory();
		Classifier wekaClassifier = factory.getComponentInstantiation(Util.createPipeline(this.getFinalSearcher(), this.getFinalEvaluator(), this.getFinalClassifier()));
		return wekaClassifier;
	}
	
	
}
