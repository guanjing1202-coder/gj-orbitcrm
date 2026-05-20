package com.orbitcrm.crm.api;

import javax.validation.constraints.NotBlank;

public class PipelineCreateRequest {
    @NotBlank
    private String pipelineName;
    private Boolean defaultPipeline;

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
}
