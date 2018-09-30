package defaultEval.core;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.lang.ProcessBuilder.Redirect;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Scanner;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.math3.util.Pair;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import defaultEval.core.Util.ParamType;
import hasco.model.BooleanParameterDomain;
import hasco.model.CategoricalParameterDomain;
import hasco.model.Component;
import hasco.model.ComponentInstance;
import hasco.model.NumericParameterDomain;
import hasco.model.Parameter;
import hasco.model.ParameterDomain;
import hasco.serialization.ComponentLoader;
import jaicore.processes.ProcessUtil;
import scala.annotation.elidable;


/**
 * Uses Hyperband to optimize hyperparameters.
 * 
 * @author Joshua
 *
 */
public class HyperbandOptimizer extends Optimizer{
	

	public HyperbandOptimizer(Component searcher, Component evaluator, Component classifier, String dataSet, File environment, File dataSetFolder, int seed, int maxRuntimeParam, int maxRuntime) {
		super(searcher, evaluator, classifier, dataSet, environment, dataSetFolder, seed, maxRuntimeParam, maxRuntime);
	}

	@Override
	public void optimize() {
		Locale.setDefault(Locale.ENGLISH);
		
		generatePyMain();
		generatePyWrapper();
		
		// run hyperband
		try {
			
			ArrayList<String> cmd = new ArrayList<>();
			cmd.add("python");
			cmd.add("-u");
			cmd.add(String.format("%s/optimizer/hyperband/main_%s.py", environment.getAbsolutePath(), buildFileName()));
			
			
			ProcessBuilder pb = new ProcessBuilder(cmd);
			pb.directory(environment.getAbsoluteFile());
			final Process proc = pb.start();
			
			
			// Thread to kill process if it takes to long
			new Thread(new Runnable() {
				@Override
				public void run() {
					long start_time = System.currentTimeMillis();
					
					while (System.currentTimeMillis() - start_time < maxRuntime*1000 && proc.isAlive()) {
						try {
							Thread.sleep(100);
						} catch (InterruptedException e) {
							e.printStackTrace();
						}
					}
					
					int id = ProcessUtil.getPID(proc);
					try {
						if(proc.isAlive()) {
							System.err.println("Kill process...");
							ProcessUtil.killProcess(id);		
						}
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			}).start();
			
			// Thread to monitor streams
			new Thread(new Runnable() {
				@Override
				public void run() {
					int r = 0;
					try {
						while ((r = proc.getErrorStream().read()) != -1) {
							System.out.write(r);
						}
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			}).start();
			
			new Thread(new Runnable() {
				@Override
				public void run() {
					int r = 0;
					try {
						while ((r = proc.getInputStream().read()) != -1) {
							System.out.write(r);
						}
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			}).start();
			
			
			proc.waitFor();
			
			createFinalInstances();
			
		} catch (IOException | ParseException e) {
			e.printStackTrace();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		System.out.println("final-searcher: " + finalSearcher);
		System.out.println("final-evaluator: " + finalEvaluator);
		System.out.println("final-classifier: " + finalClassifier);
	}

	/**
	 * Creates the final Instances of the Components by reading the Parameters found by Hyperband from the result file
	 * 
	 * @throws FileNotFoundException
	 * @throws IOException
	 * @throws ParseException
	 */
	private void createFinalInstances() throws FileNotFoundException, IOException, ParseException {
		Scanner sc = new Scanner(new File(environment.getAbsolutePath() + "/hyperband-output/" + buildFileName() + ".json"));
		StringBuilder sb = new StringBuilder();
		while (sc.hasNextLine()) {
			sb.append(sc.nextLine());
			sb.append("\r\n");
		}
		sc.close();
		
		// find best result
		JSONArray resultList = new JSONArray(sb.toString());
		JSONObject bestResult = null;
		for (int i = 0; i < resultList.length(); i++) {
			JSONObject resultInstance = resultList.getJSONObject(i);
			if(bestResult == null || resultInstance.getDouble("loss") < bestResult.getDouble("loss")) {
				bestResult = resultInstance;
			}
		}
		
		JSONObject params = bestResult.getJSONObject("params");
		
		HashMap<String, String> searcherParameter = new HashMap<>();
		HashMap<String, String> evaluatorParameter = new HashMap<>();
		HashMap<String, String> classifierParameter = new HashMap<>();
		
		if(searcher != null) {
			// get preprocessor config
			for (Parameter parameter : searcher.getParameters()) {
				searcherParameter.put(parameter.getName(), params.get(getUniqueParamName(parameter, ParamType.searcher)).toString());
			}
			for (Parameter parameter : evaluator.getParameters()) {
				evaluatorParameter.put(parameter.getName(), params.get(getUniqueParamName(parameter, ParamType.evaluator)).toString());
			}
		}
		for (Parameter parameter : classifier.getParameters()) {
			classifierParameter.put(parameter.getName(), params.get(getUniqueParamName(parameter, ParamType.classifier)).toString());
		}
		
		finalSearcher = new ComponentInstance(searcher, searcherParameter, new HashMap<>());
		finalEvaluator = new ComponentInstance(evaluator, evaluatorParameter, new HashMap<>());
		finalClassifier = new ComponentInstance(classifier, classifierParameter, new HashMap<>());
		
	}

	/**
	 * Generates a Wrapper file, to be called by Hyperband, that starts the PipelineEvaluator.jar and return the results
	 */
	private void generatePyWrapper() {
		// generate py-wrapper file
		PrintStream pyWrapperStream = null;
		
		try {
			pyWrapperStream = new PrintStream(new FileOutputStream(new File(environment.getAbsolutePath() + "/optimizer/hyperband/generated/" + "wrapper_" + buildFileName() + ".py")));
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
		
		pyWrapperStream.println("from common_defs import *");
		pyWrapperStream.println("import sys, math");
		pyWrapperStream.println("import random");
		pyWrapperStream.println("from subprocess import call");
		
		pyWrapperStream.println("def get_params():");
		pyWrapperStream.println("\tparams = {");
		
		for (Pair<Parameter, ParamType> parameterPair : parameterList) {
			pyWrapperStream.println("\t\t" + getSpaceEntryByDomain(parameterPair) + ",");	
		}
		pyWrapperStream.println("\t}");
		
		pyWrapperStream.println("\treturn handle_integers( params )");
		
		
		pyWrapperStream.println("def try_params( n_iterations, params ):");
		pyWrapperStream.println(String.format("\tcall([\"java\", \"-jar\", \"%s/PipelineEvaluator.jar\", \"%s\", \"%s\", \"%d\", %s %s %s %s %s %s])",
				environment.getAbsolutePath(),
				environment.getAbsolutePath() + "/results/" + buildFileName() + ".txt", 
				dataSetFolder.getAbsolutePath() + "/" + dataSet + ".arff",
				seed,
				(searcher != null) ? "\"" + searcher.getName() +"\", " : "\"null\", ",
				(searcher != null) ? generateParamList(searcher, ParamType.searcher) : "",
				(searcher != null) ? "\""+evaluator.getName()+"\", " : "",
				(searcher != null) ? generateParamList(evaluator, ParamType.evaluator) : "",
						"\""+classifier.getName()+"\", ",
				generateParamList(classifier, ParamType.classifier)
				));
		pyWrapperStream.println(String.format("\tfile = open(\"%s/results/%s.txt\", \"r\")", environment.getAbsolutePath(), buildFileName()));
		
		pyWrapperStream.println("\treturn {'loss': float(file.read())}");
		
		pyWrapperStream.close();
		
	}
	
	/**
	 * Generate a script to Start the optimization process
	 */
	private void generatePyMain() {
		// generate py-wrapper file
		PrintStream pyWrapperStream = null;
		
		try {
			pyWrapperStream = new PrintStream(new FileOutputStream(new File(environment.getAbsolutePath() + "/optimizer/hyperband/" + "main_" + buildFileName() + ".py")));
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
		
		pyWrapperStream.println("#!/usr/bin/env python");
		pyWrapperStream.println("from hyperband import Hyperband");
		pyWrapperStream.println("import json");
		pyWrapperStream.println(String.format("from generated.wrapper_%s import get_params, try_params", buildFileName()));
		
		pyWrapperStream.println("hb = Hyperband( get_params, try_params )");
		pyWrapperStream.println(String.format("results = hb.run(output_path=\"%s\", timeout=%d)", environment.getAbsolutePath() + "/hyperband-output/" + buildFileName() + ".json", maxRuntimeParam));
		
		pyWrapperStream.close();
	}
	
	// PRIVATE Methods to deal with Naming and Parameter conversion
	
	private String createDomainWrapper(String input, ParameterDomain pd) {
		if(pd instanceof NumericParameterDomain) {
			NumericParameterDomain n_pd = (NumericParameterDomain) pd;
			return n_pd.isInteger() ? "str(" + input + ")" : "\"{:.9f}\".format("+input+")";
		}
		
		return "str(" + input + ")";
	}
	
	@Override
	protected String buildFileName() {
		return super.buildFileName() + "Hyperband";
	}
	
	
	private String generateParamList(Component c, ParamType t) {
		StringBuilder sb = new StringBuilder();
		
		for (Parameter parameter : c.getParameters()) {
			sb.append("\"" + parameter.getName() + "\", ");
			sb.append(String.format("%s,", createDomainWrapper(String.format("params['%s']", getUniqueParamName(parameter, t)), parameter.getDefaultDomain())));
		}
		
		return sb.toString();
	}
	
	
	private String getSpaceEntryByDomain(Pair<Parameter, ParamType> p) {
		ParameterDomain pd = p.getFirst().getDefaultDomain();

		// Numeric (integer or real/double)
		if(pd instanceof NumericParameterDomain) {
			NumericParameterDomain n_pd = (NumericParameterDomain) pd;
			
			if(n_pd.isInteger()) {
				// int
				return String.format("'%s': np.random.choice(range(%d, %d, 1))", getUniqueParamName(p), (int)n_pd.getMin(), (int)n_pd.getMax()+1);
			}else {
				// float
				return String.format("'%s': np.random.uniform(%f, %f)", getUniqueParamName(p), n_pd.getMin(), n_pd.getMax());
			}	
		}
		
		// Boolean (categorical)
		else if(pd instanceof BooleanParameterDomain) {
			BooleanParameterDomain b_pd = (BooleanParameterDomain) pd;
			return String.format("'%s': np.random.choice(['true', 'false'])",getUniqueParamName(p),getUniqueParamName(p));
		}
		
		//categorical
		else if(pd instanceof CategoricalParameterDomain) {
			CategoricalParameterDomain c_pd = (CategoricalParameterDomain) pd;
			
			StringBuilder sb = new StringBuilder(String.format("'%s': np.random.choice( [", getUniqueParamName(p), getUniqueParamName(p)));
			for (int i = 0; i < c_pd.getValues().length; i++) {
				sb.append("'" + c_pd.getValues()[i] + "'");
				
				if(i != c_pd.getValues().length - 1) {
					sb.append(",");
				}
			}
			sb.append("])");

			return sb.toString();
		}
		return "";
	}
	
	
	
}
