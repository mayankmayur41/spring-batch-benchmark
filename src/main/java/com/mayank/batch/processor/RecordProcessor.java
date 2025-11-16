package com.mayank.batch.processor;

import com.mayank.batch.model.Record;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.stereotype.Component;

@Component
public class RecordProcessor implements ItemProcessor<Record, Record> {

    @Override
    public Record process(Record item) throws Exception {
        // Simple pass-through processor for benchmarking
        return item;
    }
}
