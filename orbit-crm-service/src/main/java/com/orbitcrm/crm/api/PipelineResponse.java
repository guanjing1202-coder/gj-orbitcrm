package com.orbitcrm.crm.api;

import java.util.ArrayList;
import java.util.List;

public class PipelineResponse {
    private Long id;
    private String pipelineName;
    private Boolean defaultPipeline;
    private List<PipelineStageResponse> stages = new ArrayList<PipelineStageResponse>();

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getPipelineName() {
        return pipelineName;
    }

    public void setPipelineName(String pipelineName) {
        this.pipelineName = pipelineName;
    }

    public Boolean getDefaultPipeline() {
        return defaultPipeline;
    }

    public void setDefaultPipeline(Boolean defaultPipeline) {
        this.defaultPipeline = defaultPipeline;
    }

    public List<PipelineStageResponse> getStages() {
        return stages;
    }

    public void setStages(List<PipelineStageResponse> stages) {
        this.stages = stages;
    }
}
