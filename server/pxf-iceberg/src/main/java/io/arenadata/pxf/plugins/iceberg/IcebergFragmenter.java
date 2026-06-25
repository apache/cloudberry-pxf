package io.arenadata.pxf.plugins.iceberg;

import io.arenadata.pxf.plugins.iceberg.catalog.CatalogProvider;
import io.arenadata.pxf.plugins.iceberg.filtering.ExpressionBuilder;
import io.arenadata.pxf.plugins.iceberg.reader.IcebergFragmentMetadata;
import io.arenadata.pxf.plugins.iceberg.table.TableWrapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.iceberg.FileScanTask;
import org.apache.iceberg.TableScan;
import org.apache.iceberg.io.CloseableIterable;
import org.apache.iceberg.types.Types;
import org.greenplum.pxf.api.model.*;
import org.greenplum.pxf.api.utilities.ColumnDescriptor;
import org.greenplum.pxf.api.utilities.SpringContext;

import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

@Slf4j
public class IcebergFragmenter extends BasePlugin implements Fragmenter {

    private static final Comparator<Fragment> fragmentComparator =
            Comparator.<Fragment, String>comparing(f -> ((IcebergFragmentMetadata)f.getMetadata()).task().file().location())
                    .thenComparing(f -> ((IcebergFragmentMetadata)f.getMetadata()).task().file().pos(),
                            Comparator.nullsLast(Comparator.naturalOrder()));

    private final CatalogProvider catalogProvider;

    private TableWrapper table;
    private IcebergSettings settings;

    public IcebergFragmenter() {
        this(SpringContext.getBean(CatalogProvider.class));
    }

    public IcebergFragmenter(CatalogProvider catalogProvider) {
        this.catalogProvider = catalogProvider;
    }

    @Override
    public void afterPropertiesSet() {
        this.settings = IcebergSettings.create(configuration, context);
        this.table = catalogProvider.get(settings).loadTable(context.getDataSource());
    }

    @Override
    public List<Fragment> getFragments() throws Exception {
        log.info("Preparing fragments for {}", context.getDataSource());
        var tableScan = buildTableScan();
        try(CloseableIterable<FileScanTask> tasks = tableScan.planFiles()) {
            var fragments = transformTasks(tasks, task -> {
                log.debug("Preparing fragments for {}", task.file().location());
                return new Fragment(
                        context.getDataSource(),
                        new IcebergFragmentMetadata(tableScan, task)
                );
            }, fragmentComparator);
            log.info("There are {} fragments for data source {}", fragments.size(), context.getDataSource());
            return fragments;
        }
    }

    @Override
    public FragmentStats getFragmentStats() throws Exception {
        var tableScan = buildTableScan();
        try(CloseableIterable<FileScanTask> tasks = tableScan.planFiles()) {
            var counts = transformTasks(tasks, task -> task.file().recordCount(), (f1, f2) -> 0);
            if(counts.isEmpty()){
                return new FragmentStats(0, 0, 0);
            }
            return new FragmentStats(counts.size(), counts.get(0), counts.stream().mapToLong(it -> it).sum());
        }
    }

    private <T> List<T> transformTasks(CloseableIterable<FileScanTask> tasks, Function<FileScanTask, T> transformer, Comparator<T> comparator){
        return StreamSupport.stream(tasks.spliterator(), false)
                .filter(task -> task.file().recordCount() > 0)
                .flatMap(task -> splitTask(task, settings.getFragmentSize()))
                .map(transformer)
                .sorted(comparator)
                .toList();
    }

    private Stream<FileScanTask> splitTask(FileScanTask input, long fragmentSize) {
        if(fragmentSize < 1) {
            return Stream.of(input);
        }
        return StreamSupport.stream(input.split(fragmentSize).spliterator(), false);
    }


    private TableScan buildTableScan() {
        var scan = table.scan();
        var icebergTypeByColumn = table.schema().columns().stream()
                .collect(Collectors.toMap(Types.NestedField::name, Types.NestedField::type));
        return scan
            .useRef(settings.getBranchName())
            // Filtering
            .filter(ExpressionBuilder.create(context.getTupleDescription(), icebergTypeByColumn).build(context.getFilterString()))
            // Select specific columns
            .select(context.getTupleDescription()
                .stream()
                .filter(ColumnDescriptor::isProjected)
                .map(ColumnDescriptor::columnName)
                .toList()
        );
    }

}
