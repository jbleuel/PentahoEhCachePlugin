package com.ehcache.pentaho.output;


/*
EhCache Pentaho Plugin
Author: Stephan Grotz (stephan.grotz@gmail.com)
URL: https://github.com/sgrotz/PentahoEhCachePlugin

Copyright (c) 2012 Stephan Grotz

Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated 
documentation files (the "Software"), to deal in the Software without restriction, including without limitation 
the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and 
to permit persons to whom the Software is furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all copies or substantial portions of 
the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO 
THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE 
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, 
TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

*/


import java.util.List;
import java.util.Map;

import net.sf.ehcache.Cache;
import net.sf.ehcache.CacheManager;
import net.sf.ehcache.Element;

import org.pentaho.di.core.Const;
import org.pentaho.di.core.ResultFile;
import org.pentaho.di.core.RowSet;
import org.pentaho.di.core.exception.KettleException;
import org.pentaho.di.core.exception.KettleStepException;
import org.pentaho.di.core.exception.KettleValueException;
import org.pentaho.di.core.logging.LogChannelInterface;
import org.pentaho.di.core.row.RowDataUtil;
import org.pentaho.di.core.row.RowMetaInterface;
import org.pentaho.di.core.row.ValueMeta;
import org.pentaho.di.core.row.ValueMetaInterface;
import org.pentaho.di.core.variables.VariableSpace;
import org.pentaho.di.trans.Trans;
import org.pentaho.di.trans.TransMeta;
import org.pentaho.di.trans.step.BaseStep;
import org.pentaho.di.trans.step.BaseStepData.StepExecutionStatus;
import org.pentaho.di.trans.step.RowListener;
import org.pentaho.di.trans.step.StepDataInterface;
import org.pentaho.di.trans.step.StepInterface;
import org.pentaho.di.trans.step.StepListener;
import org.pentaho.di.trans.step.StepMeta;
import org.pentaho.di.trans.step.StepMetaInterface;
import org.pentaho.di.core.row.ValueMetaAndData;


public class EhCachePluginOutput extends BaseStep implements StepInterface {

	public EhCachePluginOutput(StepMeta stepMeta,
			StepDataInterface stepDataInterface, int copyNr,
			TransMeta transMeta, Trans trans) {
		
		super(stepMeta, stepDataInterface, copyNr, transMeta, trans);
		// TODO Auto-generated constructor stub
	}

	private EhCachePluginOutputData data;
	private EhCachePluginOutputMeta meta;
	private CacheManager manager;
	private Cache cache;

	@Override
	public void dispose(StepMetaInterface iMeta, StepDataInterface iData) {
		// TODO Auto-generated method stub

		meta = (EhCachePluginOutputMeta) iMeta;
		data = (EhCachePluginOutputData) iData;

		// manager.shutdown();

		super.dispose(iMeta, iData);

	}

	@Override
	public boolean init(StepMetaInterface iMeta, StepDataInterface iData) {
		// TODO Auto-generated method stub

		meta = (EhCachePluginOutputMeta) iMeta;
		data = (EhCachePluginOutputData) iData;

		String cacheName = meta.getCacheName();
		String xmlURL = meta.getXmlURL();


		if ((xmlURL == null) || (cacheName == null)) {
			meta.setDefault();
		}

		logDebug("*** Using Cache: " + cacheName + " from configuration file: " + xmlURL);

		if ((cacheName != null) && (xmlURL != null)) {
			manager = CacheManager.newInstance(xmlURL);
			cache = manager.getCache(cacheName);
		} else {
			logError("*** No cacheName or XML URL was specified ...");
		}

		return super.init(iMeta, iData);
	}


	@Override
	public boolean processRow(StepMetaInterface iMeta, StepDataInterface iData)
			throws KettleException {
		// TODO Auto-generated method stub

		meta = (EhCachePluginOutputMeta) iMeta;
		data = (EhCachePluginOutputData) iData;
		
		// Make sure to check the cache exists before processing the entries ...
		if (!manager.cacheExists(meta.getCacheName())) {
			logError("*** Cache " + meta.getCacheName() + " does not exist in the ehcache.xml configuration file... ");
			
			// If the cache does not exist, cancel all operations and exit!
			setErrors(1);
			stopAll();
		}
		
		// If all objects have been processed, exit here ... 
		Object[] obj = getRow();
		if (obj==null) {
			logDebug("*** There is no more input ... Nothing to add to the cache! :(");
			setOutputDone();
			return false;
		}

		if (first)
		{
			logDebug("*** This is the first record ...");
			first = false;
			data.outputRowMeta = (RowMetaInterface)getInputRowMeta().clone();
			meta.getFields(data.outputRowMeta, getStepname(), null, null, this); 
		}

		// Get the ID's and Values from the input rows...
		String value = data.outputRowMeta.getString(obj, data.outputRowMeta.indexOfValue("VALUE"));
		String ID = data.outputRowMeta.getString(obj, data.outputRowMeta.indexOfValue("KEY"));

		if ((ID != null) && (value != null)) {
			cache = manager.getCache(meta.getCacheName());
			
			// Add the elements to the cache...
			logDebug("*** Writing new Element: " + ID + " with value " + value);     
			cache.put(new Element(ID, value));

			if (checkFeedback(linesRead)) logBasic("Linenr "+linesRead);

			incrementLinesWritten();
			return true;
		} else {
			// Throw an error when the ID or Value was null
			logError("*** KEY or VALUE field was null ...");     
			return false;
		}
	}


	@SuppressWarnings("deprecation")
	public void run()
	{
		logBasic("*** Starting to run...");
		try
		{
			while (processRow(meta, data) && !isStopped());
		}
		catch(Exception e)
		{
			logError("Unexpected error : "+e.toString());
			logError(Const.getStackTracker(e));
			setErrors(1);
			stopAll();
		}
		finally
		{
			dispose(meta, data);
			logBasic("Finished, processing "+linesRead+" rows");
			markStop();
		}
	}


}
