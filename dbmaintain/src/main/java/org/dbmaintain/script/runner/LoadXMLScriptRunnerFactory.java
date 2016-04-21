/*
 * Copyright DbMaintain.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.dbmaintain.script.runner;

import static org.dbmaintain.config.DbMaintainProperties.PROPERTY_LOAD_XML_FUNCTION;

import org.dbmaintain.config.FactoryWithDatabase;
import org.dbmaintain.config.PropertyUtils;
import org.dbmaintain.script.runner.impl.LoadXMLScriptRunner;

/**
 * @author ARGUS
 */
public class LoadXMLScriptRunnerFactory extends FactoryWithDatabase<ScriptRunner> {


    public ScriptRunner createInstance() {
    	String loadXMLFunction = PropertyUtils.getString(PROPERTY_LOAD_XML_FUNCTION, getConfiguration());
        return new LoadXMLScriptRunner(getDatabases(), getSqlHandler(), loadXMLFunction);
    }
}
