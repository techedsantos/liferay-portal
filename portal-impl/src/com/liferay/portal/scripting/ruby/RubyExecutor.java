/**
 * Copyright (c) 2000-2011 Liferay, Inc. All rights reserved.
 *
 * This library is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 */

package com.liferay.portal.scripting.ruby;

import com.liferay.portal.kernel.scripting.BaseScriptingExecutor;
import com.liferay.portal.kernel.scripting.ExecutionException;
import com.liferay.portal.kernel.scripting.ScriptingException;
import com.liferay.portal.kernel.servlet.WebDirDetector;
import com.liferay.portal.kernel.util.PortalClassLoaderUtil;
import com.liferay.portal.kernel.util.StringPool;
import com.liferay.portal.util.PropsValues;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jruby.RubyInstanceConfig;
import org.jruby.RubyInstanceConfig.CompileMode;
import org.jruby.embed.LocalContextScope;
import org.jruby.embed.ScriptingContainer;
import org.jruby.exceptions.RaiseException;

/**
 * @author Alberto Montero
 * @author Raymond Augé
 */
public class RubyExecutor extends BaseScriptingExecutor {

	public static final String LANGUAGE = "ruby";

	public RubyExecutor() {
		_ruby = new ScriptingContainer(LocalContextScope.THREADSAFE);

		RubyInstanceConfig rubyInstanceConfig =
			_ruby.getProvider().getRubyInstanceConfig();

		rubyInstanceConfig.setLoader(PortalClassLoaderUtil.getClassLoader());

		if (PropsValues.SCRIPTING_JRUBY_COMPILE_MODE.equals(
				_COMPILE_MODE_FORCE)) {

			rubyInstanceConfig.setCompileMode(CompileMode.FORCE);
		}
		else if (PropsValues.SCRIPTING_JRUBY_COMPILE_MODE.equals(
					_COMPILE_MODE_JIT)) {

			rubyInstanceConfig.setCompileMode(CompileMode.JIT);
		}

		rubyInstanceConfig.setJitThreshold(
			PropsValues.SCRIPTING_JRUBY_COMPILE_THRESHOLD);

		_basePath = WebDirDetector.getRootDir(
			PortalClassLoaderUtil.getClassLoader());

		_loadPaths = new ArrayList<String>(3);

		_loadPaths.add("META-INF/jruby.home/lib/ruby/1.8");
		_loadPaths.add("META-INF/jruby.home/lib/ruby/site_ruby/1.8");
		_loadPaths.add(
			"file:" + _basePath +
				"WEB-INF/lib/ruby-gems.jar!/gems/haml-3.0.25/lib");

		rubyInstanceConfig.setLoadPaths(_loadPaths);

		_ruby.setCurrentDirectory(_basePath);
	}

	public Map<String, Object> eval(
			Set<String> allowedClasses, Map<String, Object> inputObjects,
			Set<String> outputNames, File scriptFile)
		throws ScriptingException {

		return eval(
			allowedClasses, inputObjects, outputNames, scriptFile, null);
	}

	public Map<String, Object> eval(
			Set<String> allowedClasses, Map<String, Object> inputObjects,
			Set<String> outputNames, String script)
		throws ScriptingException {

		return eval(allowedClasses, inputObjects, outputNames, null, script);
	}

	public String getLanguage() {
		return LANGUAGE;
	}

	protected Map<String, Object> eval(
			Set<String> allowedClasses, Map<String, Object> inputObjects,
			Set<String> outputNames, File scriptFile, String script)
		throws ScriptingException {

		if (allowedClasses != null) {
			throw new ExecutionException(
				"Constrained execution not supported for Ruby");
		}

		try {
			RubyInstanceConfig rubyInstanceConfig =
				_ruby.getProvider().getRubyInstanceConfig();

			rubyInstanceConfig.setCurrentDirectory(_basePath);
			rubyInstanceConfig.setLoadPaths(_loadPaths);

//			GlobalVariables globalVariables = _ruby.getGlobalVariables();

			for (Map.Entry<String, Object> entry : inputObjects.entrySet()) {
				String inputName = entry.getKey();
				Object inputObject = entry.getValue();

				if (!inputName.startsWith(StringPool.DOLLAR)) {
					inputName = StringPool.DOLLAR + inputName;
				}

//				BeanGlobalVariable beanGlobalVariable = new BeanGlobalVariable(
//					_ruby, inputObject, inputObject.getClass());

				_ruby.put(inputName, inputObject);
			}

			if (scriptFile != null) {
				_ruby.runScriptlet(
					new FileInputStream(scriptFile), scriptFile.toString());
			}
			else {
				_ruby.runScriptlet(script);
			}

			if (outputNames == null) {
				return null;
			}

			Map<String, Object> outputObjects = new HashMap<String, Object>();

			for (String outputName : outputNames) {
				outputObjects.put(outputName, _ruby.get(outputName));
			}

			return outputObjects;
		}
		catch (RaiseException re) {
			throw new ScriptingException(
				re.getException().message.asJavaString() + "\n\n", re);
		}
		catch (FileNotFoundException fnfe) {
			throw new ScriptingException(fnfe);
		}
	}

	private static final String _COMPILE_MODE_FORCE = "force";

	private static final String _COMPILE_MODE_JIT = "jit";

	private String _basePath;
	private List<String> _loadPaths;
	private ScriptingContainer _ruby;

}