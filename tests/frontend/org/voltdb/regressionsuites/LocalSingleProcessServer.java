/* This file is part of VoltDB.
 * Copyright (C) 2008-2010 VoltDB L.L.C.
 *
 * Permission is hereby granted, free of charge, to any person obtaining
 * a copy of this software and associated documentation files (the
 * "Software"), to deal in the Software without restriction, including
 * without limitation the rights to use, copy, modify, merge, publish,
 * distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to
 * the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS BE LIABLE FOR ANY CLAIM, DAMAGES OR
 * OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE,
 * ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 */

package org.voltdb.regressionsuites;

import java.util.ArrayList;
import java.util.List;
import org.voltdb.BackendTarget;
import org.voltdb.ProcedureProfiler;
import org.voltdb.ServerThread;
import org.voltdb.VoltDB.Configuration;
import org.voltdb.compiler.VoltProjectBuilder;
import org.voltdb.jni.ExecutionEngineIPC;

/**
 * Implementation of a VoltServerConfig for the simplest case:
 * the single-process VoltServer that's so easy to use.
 *
 */
public class LocalSingleProcessServer implements VoltServerConfig {

    public final String m_jarFileName;
    public final int m_siteCount;
    public final BackendTarget m_target;

    ServerThread m_server = null;
    boolean m_compiled = false;
    protected String m_pathToDeployment;

    public LocalSingleProcessServer(String jarFileName, int siteCount,
                                    BackendTarget target)
    {
        assert(jarFileName != null);
        assert(siteCount > 0);
        final String buildType = System.getenv().get("BUILD");
        m_jarFileName = Configuration.getPathToCatalogForTest(jarFileName);
        m_siteCount = siteCount;
        if (buildType == null) {
            m_target = target;
        } else {
            if (buildType.startsWith("memcheck")) {
                if (target.equals(BackendTarget.NATIVE_EE_JNI)) {
                    m_target = BackendTarget.NATIVE_EE_VALGRIND_IPC;
                } else {
                    m_target = target;//For memcheck
                }
            } else {
                m_target = target;
            }
        }
    }
    @Override
    public boolean compile(VoltProjectBuilder builder) {
        if (m_compiled == true) {
            return true;
        }

        m_compiled = builder.compile(m_jarFileName, m_siteCount, 1, 0, "localhost");
        m_pathToDeployment = builder.getPathToDeployment();

        return m_compiled;
    }

    @Override
    public List<String> getListenerAddresses() {
        // return just "localhost"
        if (m_server == null)
            return null;
        ArrayList<String> listeners = new ArrayList<String>();
        listeners.add("localhost");
        return listeners;
    }

    @Override
    public String getName() {
        // name is combo of the classname and the parameters

        String retval = "localSingleProcess-";
        retval += String.valueOf(m_siteCount);
        if (m_target == BackendTarget.HSQLDB_BACKEND)
            retval += "-HSQL";
        else if (m_target == BackendTarget.NATIVE_EE_IPC)
            retval += "-IPC";
        else
            retval += "-JNI";
        return retval;
    }

    @Override
    public int getNodeCount()
    {
        return 1;
    }

    @Override
    public List<String> shutDown() throws InterruptedException {
        m_server.shutdown();
        if (m_target == BackendTarget.NATIVE_EE_VALGRIND_IPC) {
            if (!ExecutionEngineIPC.m_valgrindErrors.isEmpty()) {
                ArrayList<String> retval = new ArrayList<String>(ExecutionEngineIPC.m_valgrindErrors);
                ExecutionEngineIPC.m_valgrindErrors.clear();
                return retval;
            }
        }
        return null;
    }

    @Override
    public void startUp() {
        Configuration config = new Configuration();
        config.m_backend = m_target;
        config.m_noLoadLibVOLTDB = (m_target == BackendTarget.HSQLDB_BACKEND);
        // m_jarFileName is already prefixed with test output path.
        config.m_pathToCatalog = m_jarFileName;
        config.m_profilingLevel = ProcedureProfiler.Level.DISABLED;
        config.m_pathToDeployment = m_pathToDeployment;

        m_server = new ServerThread(config);
        m_server.start();
        m_server.waitForInitialization();
    }

    @Override
    public boolean isHSQL() {
        return m_target == BackendTarget.HSQLDB_BACKEND;
    }

    @Override
    public boolean isValgrind() {
        return m_target == BackendTarget.NATIVE_EE_VALGRIND_IPC;
    }
}
