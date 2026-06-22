package com.genchat.agent.deepresearch;

import com.genchat.common.ToolRecord;
import com.genchat.entity.SearchResult;
import lombok.Getter;
import lombok.Setter;
import reactor.core.Disposable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@Getter
@Setter
public class DeepResearchRunContext {

    private final DeepResearchDependencies deps;
    private final List<ToolRecord> toolRecords = Collections.synchronizedList(new ArrayList<>());
    private final List<SearchResult> allReferences = new ArrayList<>();
    private Disposable.Composite compositeDisposable;
    private Long currentSessionId;
    private long startTime;
    private long firstResponseTime;
    private int contextCharLimit = 50000;

    public DeepResearchRunContext(DeepResearchDependencies deps) {
        this.deps = deps;
    }

    public boolean isStopped(AtomicBoolean finished) {
        return finished.get() || compositeDisposable.isDisposed();
    }
}
