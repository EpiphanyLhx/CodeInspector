package com.codeinspector.model.vo;

import lombok.Data;
import java.util.Map;

@Data
public class IssueStatsVO {
    private int totalIssues;
    private Map<String, Long> severityStats;
    private Map<String, Long> categoryStats;
}
