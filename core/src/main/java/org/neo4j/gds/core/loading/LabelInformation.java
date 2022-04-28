/*
 * Copyright (c) "Neo4j"
 * Neo4j Sweden AB [http://neo4j.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.gds.core.loading;

import com.carrotsearch.hppc.BitSet;
import com.carrotsearch.hppc.IntObjectMap;
import org.neo4j.gds.ElementIdentifier;
import org.neo4j.gds.NodeLabel;
import org.neo4j.gds.api.IdMap;
import org.neo4j.gds.core.utils.paged.HugeAtomicGrowingBitSet;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongUnaryOperator;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static org.neo4j.gds.core.GraphDimensions.ANY_LABEL;
import static org.neo4j.gds.utils.StringFormatting.formatWithLocale;

public final class LabelInformation {

    private static final Set<NodeLabel> ALL_NODES_LABELS = Set.of(NodeLabel.ALL_NODES);

    public static LabelInformation from(Map<NodeLabel, BitSet> labelInformation) {
        return new LabelInformation(labelInformation);
    }

    private final Map<NodeLabel, BitSet> labelInformation;

    private LabelInformation(Map<NodeLabel, BitSet> labelInformation) {
        this.labelInformation = labelInformation;
    }

    public boolean isEmpty() {
        return labelInformation.isEmpty();
    }

    public Set<NodeLabel> labelSet() {
        return labelInformation.keySet();
    }

    public void forEach(LabelInformationConsumer consumer) {
        for (Map.Entry<NodeLabel, BitSet> entry : labelInformation.entrySet()) {
            if (!consumer.accept(entry.getKey(), entry.getValue())) {
                return;
            }
        }
    }

    public LabelInformation filter(Collection<NodeLabel> nodeLabels) {
        return new LabelInformation(nodeLabels
            .stream()
            .collect(Collectors.toMap(nodeLabel -> nodeLabel, labelInformation::get)));
    }

    public BitSet unionBitSet(Collection<NodeLabel> nodeLabels, long nodeCount) {
        BitSet unionBitSet = new BitSet(nodeCount);
        nodeLabels.forEach(label -> unionBitSet.union(labelInformation.get(label)));
        return unionBitSet;
    }

    public boolean hasLabel(long nodeId, NodeLabel nodeLabel) {
        if (labelInformation.isEmpty() && nodeLabel.equals(NodeLabel.ALL_NODES)) {
            return true;
        }
        var bitSet = labelInformation.get(nodeLabel);
        return bitSet != null && bitSet.get(nodeId);
    }

    public Set<NodeLabel> availableNodeLabels() {
        return labelInformation.isEmpty()
            ? ALL_NODES_LABELS
            : labelSet();
    }

    public Set<NodeLabel> nodeLabelsForNodeId(long nodeId) {
        if (isEmpty()) {
            return ALL_NODES_LABELS;
        } else {
            Set<NodeLabel> set = new HashSet<>();
            forEach((nodeLabel, bitSet) -> {
                if (bitSet.get(nodeId)) {
                    set.add(nodeLabel);
                }
                return true;
            });
            return set;
        }
    }

    public void forEachNodeLabel(long nodeId, IdMap.NodeLabelConsumer consumer) {
        if (isEmpty()) {
            consumer.accept(NodeLabel.ALL_NODES);
        } else {
            forEach((nodeLabel, bitSet) -> {
                if (bitSet.get(nodeId)) {
                    return consumer.accept(nodeLabel);
                }
                return true;
            });
        }
    }

    public void validateNodeLabelFilter(Collection<NodeLabel> nodeLabels) {
        List<ElementIdentifier> invalidLabels = nodeLabels
            .stream()
            .filter(label -> !new HashSet<>(labelSet()).contains(label))
            .collect(Collectors.toList());
        if (!invalidLabels.isEmpty()) {
            throw new IllegalArgumentException(formatWithLocale(
                "Specified labels %s do not correspond to any of the node projections %s.",
                invalidLabels,
                labelSet()
            ));
        }
    }

    public interface LabelInformationConsumer {
        boolean accept(NodeLabel nodeLabel, BitSet bitSet);
    }

    public static Builder emptyBuilder() {
        return Builder.of();
    }

    public static Builder builder(long expectedCapacity) {
        return Builder.of(expectedCapacity);
    }

    public static Builder builder(
        long expectedCapacity,
        IntObjectMap<List<NodeLabel>> labelTokenNodeLabelMapping
    ) {
        return Builder.of(expectedCapacity, labelTokenNodeLabelMapping);
    }

    public static final class Builder {
        private final long expectedCapacity;
        final Map<NodeLabel, HugeAtomicGrowingBitSet> labelInformation;
        private final List<NodeLabel> starNodeLabelMappings;

        private Builder(
            long expectedCapacity,
            Map<NodeLabel, HugeAtomicGrowingBitSet> labelInformation,
            List<NodeLabel> starNodeLabelMappings
        ) {
            this.expectedCapacity = expectedCapacity;
            this.labelInformation = labelInformation;
            this.starNodeLabelMappings = starNodeLabelMappings;
        }

        static Builder of() {
            return of(0);
        }

        static Builder of(long expectedCapacity) {
            return new Builder(expectedCapacity, new ConcurrentHashMap<>(), List.of());
        }

        static Builder of(
            long expectedCapacity,
            IntObjectMap<List<NodeLabel>> labelTokenNodeLabelMapping
        ) {
            var starNodeLabelMappings = labelTokenNodeLabelMapping.getOrDefault(ANY_LABEL, List.of());

            var nodeLabelBitSetMap = prepareLabelMap(
                labelTokenNodeLabelMapping,
                () -> HugeAtomicGrowingBitSet.create(expectedCapacity)
            );

            return new Builder(expectedCapacity, nodeLabelBitSetMap, starNodeLabelMappings);
        }

        private static <T> Map<NodeLabel, T> prepareLabelMap(
            IntObjectMap<List<NodeLabel>> labelTokenNodeLabelMapping,
            Supplier<T> mapSupplier
        ) {
            return StreamSupport.stream(
                    labelTokenNodeLabelMapping.values().spliterator(),
                    false
                )
                .flatMap(cursor -> cursor.value.stream())
                .distinct()
                .collect(Collectors.toMap(
                        nodeLabel -> nodeLabel,
                        nodeLabel -> mapSupplier.get()
                    )
                );
        }

        public void addNodeIdToLabel(NodeLabel nodeLabel, long nodeId) {
            labelInformation
                .computeIfAbsent(
                    nodeLabel,
                    (ignored) -> HugeAtomicGrowingBitSet.create(expectedCapacity)
                ).set(nodeId);
        }

        Map<NodeLabel, BitSet> buildInner(long nodeCount, LongUnaryOperator mappedIdFn) {
            return this.labelInformation
                .entrySet()
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> {
                    var importBitSet = e.getValue();
                    var internBitSet = new BitSet(nodeCount);

                    importBitSet.forEachSetBit(neoId -> internBitSet.set(mappedIdFn.applyAsLong(neoId)));

                    return internBitSet;
                }));
        }

        public LabelInformation build(long nodeCount, LongUnaryOperator mappedIdFn) {
            var labelInformation = buildInner(nodeCount, mappedIdFn);

            // set the whole range for '*' projections
            for (NodeLabel starLabel : starNodeLabelMappings) {
                var bitSet = new BitSet(nodeCount);
                bitSet.set(0, nodeCount);
                labelInformation.put(starLabel, bitSet);
            }

            return new LabelInformation(labelInformation);
        }
    }
}
