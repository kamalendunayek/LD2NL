/*
 * #%L
 * SPARQL2NL
 * %%
 * Copyright (C) 2015 Agile Knowledge Engineering and Semantic Web (AKSW)
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.aksw.sparql2nl.similarity;

import java.util.HashMap;
import java.util.TreeSet;
import java.util.regex.Pattern;
import org.aksw.sparql2nl.queryprocessing.Query;
import simpack.accessor.graph.SimpleGraphAccessor;
import simpack.api.IGraphNode;
import simpack.measure.graph.GraphIsomorphism;
import simpack.measure.graph.SubgraphIsomorphism;

/**
 *
 * @author ngonga
 */
public class TypeAwareGraphIsomorphism implements QuerySimilarity {

    /** Computes size of small graph isomorphism and norms it with
     * size of graphs
     * @param q1 First query
     * @param q2 Second query
     * @return Similarity
     */
//    @Override
    public double getSimilarity(Query q1, Query q2) {
        SimpleGraphAccessor g1 = q1.getGraphRepresentation();
        SimpleGraphAccessor g2 = q2.getGraphRepresentation();
        GraphIsomorphism gi = new GraphIsomorphism(g1, g2);
        gi.calculate();
        if (gi.getGraphIsomorphism() == 1) {
            return 0.5 + 0.5 * typeAwareDirectionalSimilarity(q1, q2, gi.getCliqueList());
        }
//        return gi.getSimilarity();

        SubgraphIsomorphism si = new SubgraphIsomorphism(g1, g2);
        Double sim = si.getSimilarity();
        if (sim != null) {
            return 0.5 * sim;
        } else {
            return 0;
        }
    }

    private double typeAwareDirectionalSimilarity(Query q1, Query q2, TreeSet<String> cliqueList) {
        SimpleGraphAccessor g1 = q1.getGraphRepresentation();
        SimpleGraphAccessor g2 = q2.getGraphRepresentation();

        HashMap<String, String> nodeMapping = new HashMap<>();
        for (String s : cliqueList) {
            String[] split1 = s.split(Pattern.quote(", "));
            for (int i = 0; i < split1.length; i++) {
                String entry = split1[i];
                String[] split = entry.split(Pattern.quote(":"));
                if (split[0].equals("rdf")) {
                    nodeMapping.put(split[0] + ":" + split[1], split[2]);
                } else if (split[1].equals("rdf")) {
                    nodeMapping.put(split[0], split[1] + ":" + split[2]);
                } else {
                    nodeMapping.put(split[0], split[1]);
                }
            }
        }
        //check whether the same vars are used
        TreeSet<String> vars1 = q1.getSelectedVars();
        TreeSet<String> vars2 = q2.getSelectedVars();
        if (vars1.size() != vars2.size()) {
            return 0;
        }
        if (vars1 != null && vars2 != null) {
            for (String var : vars1) {
                if (!vars2.contains(nodeMapping.get(var))) {
                    return 0;
                }
            }
        }
        //get successor map at string level
        HashMap<String, TreeSet<String>> successors1 = new HashMap<>();
        double edgeCount1 = 0;
        for (IGraphNode n : g1.getNodeSet()) {
            TreeSet<IGraphNode> succ = n.getSuccessorSet();
            TreeSet<String> labels = new TreeSet<>();
            for (IGraphNode ns : succ) {
                labels.add(ns.getLabel());
                edgeCount1++;
            }
            successors1.put(n.getLabel(), labels);
        }

        HashMap<String, TreeSet<String>> successors2 = new HashMap<>();
        double edgeCount2 = 0;
        for (IGraphNode n : g2.getNodeSet()) {
            TreeSet<IGraphNode> succ = n.getSuccessorSet();
            TreeSet<String> labels = new TreeSet<>();
            for (IGraphNode ns : succ) {
                labels.add(ns.getLabel());
                edgeCount2++;
            }
            successors2.put(n.getLabel(), labels);
        }

        //now compare common edges
        double count = 0;
        try {
            for (String node1 : nodeMapping.keySet()) {
                String node2 = nodeMapping.get(node1);
                //check for the source of the edge and make sure they are of the same 
                //type
                if ((node1.equals("rdf:type") && node2.equals("rdf:type"))
                        || (!node2.equals("rdf:type") && !node1.equals("rdf:type"))) {
                    TreeSet<String> succ2 = successors2.get(node2);
                    for (String succ1 : successors1.get(node1)) {
                        if (succ2.contains(nodeMapping.get(succ1))) {
                            count++;
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error parsing");
            e.printStackTrace();
            return 0;
        }

        return 2 * count / (edgeCount1 + edgeCount2);
    }
}
