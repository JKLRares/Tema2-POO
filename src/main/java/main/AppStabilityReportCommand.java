package main;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class AppStabilityReportCommand implements Command {

    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public void execute(InputCommand input, Database db, List<ObjectNode> outputs) {
        User user = db.getUser(input.username);

        if (user == null) {
            ObjectNode error = mapper.createObjectNode();
            error.put("command", input.command);
            error.put("username", input.username);
            error.put("timestamp", input.timestamp);
            error.put("error", "The user " + input.username + " does not exist.");
            outputs.add(error);
            return;
        }

        if (user.getRole() != UserRole.MANAGER) {
            ObjectNode error = mapper.createObjectNode();
            error.put("command", input.command);
            error.put("username", input.username);
            error.put("timestamp", input.timestamp);
            error.put("error", "The user " + input.username + " does not have permission to execute this command: required role MANAGER; user role " + user.getRole() + ".");
            outputs.add(error);
            return;
        }

        int totalActiveTickets = 0;

        Map<String, Double> totalRiskSumByType = new LinkedHashMap<>();
        Map<String, Double> totalImpactSumByType = new LinkedHashMap<>();
        Map<String, Integer> countByType = new LinkedHashMap<>();
        Map<String, Integer> ticketsByPriority = new LinkedHashMap<>();

        String[] types = {"BUG", "FEATURE_REQUEST", "UI_FEEDBACK"};
        for (String type : types) {
            totalRiskSumByType.put(type, 0.0);
            totalImpactSumByType.put(type, 0.0);
            countByType.put(type, 0);
        }

        ticketsByPriority.put("LOW", 0);
        ticketsByPriority.put("MEDIUM", 0);
        ticketsByPriority.put("HIGH", 0);
        ticketsByPriority.put("CRITICAL", 0);

        for (Ticket t : db.getTickets()) {
            if (t.getStatus() == Status.OPEN || t.getStatus() == Status.IN_PROGRESS) {
                totalActiveTickets++;
                String typeStr = t.getType().toString();

                if (countByType.containsKey(typeStr)) {
                    countByType.put(typeStr, countByType.get(typeStr) + 1);
                    totalRiskSumByType.put(typeStr, totalRiskSumByType.get(typeStr) + t.calculateRisk());
                    totalImpactSumByType.put(typeStr, totalImpactSumByType.get(typeStr) + t.calculateImpact());
                }

                String priorityStr = t.getPriority().toString();
                ticketsByPriority.put(priorityStr, ticketsByPriority.getOrDefault(priorityStr, 0) + 1);
            }
        }

        String stabilityStatus;

        if (totalActiveTickets == 0) {
            stabilityStatus = "STABLE";
        } else {
            boolean hasSignificantOrMajorRisk = false;
            boolean allNegligibleAndLowImpact = true;

            for (String type : types) {
                int count = countByType.get(type);
                if (count > 0) {
                    double totalRisk = totalRiskSumByType.get(type);
                    double totalImpact = totalImpactSumByType.get(type);

                    double avgRisk = totalRisk / count;
                    double avgImpact = totalImpact / count;

                    String riskQualifier = getRiskQualifier(avgRisk);

                    if ("SIGNIFICANT".equals(riskQualifier) || "MAJOR".equals(riskQualifier)) {
                        hasSignificantOrMajorRisk = true;
                    }

                    boolean isNegligible = "NEGLIGIBLE".equals(riskQualifier);
                    boolean isLowImpact = avgImpact < 50;

                    if (!isNegligible || !isLowImpact) {
                        allNegligibleAndLowImpact = false;
                    }
                }
            }

            if (hasSignificantOrMajorRisk) {
                stabilityStatus = "UNSTABLE";
            } else if (allNegligibleAndLowImpact) {
                stabilityStatus = "STABLE";
            } else {
                stabilityStatus = "PARTIALLY STABLE";
            }
        }

        ObjectNode resultNode = mapper.createObjectNode();
        resultNode.put("command", input.command);
        resultNode.put("username", input.username);
        resultNode.put("timestamp", input.timestamp);

        ObjectNode reportNode = mapper.createObjectNode();
        reportNode.put("totalOpenTickets", totalActiveTickets);

        ObjectNode typeNode = mapper.createObjectNode();
        countByType.forEach(typeNode::put);
        reportNode.set("openTicketsByType", typeNode);

        ObjectNode priorityNode = mapper.createObjectNode();
        ticketsByPriority.forEach(priorityNode::put);
        reportNode.set("openTicketsByPriority", priorityNode);

        ObjectNode riskNode = mapper.createObjectNode();
        ObjectNode impactNode = mapper.createObjectNode();

        for (String type : types) {
            int count = countByType.get(type);
            double totalRisk = totalRiskSumByType.get(type);
            double totalImpact = totalImpactSumByType.get(type);

            double avgRisk = (count > 0) ? (totalRisk / count) : 0.0;
            double avgImpact = (count > 0) ? (totalImpact / count) : 0.0;

            riskNode.put(type, getRiskQualifier(avgRisk));
            impactNode.put(type, Math.round(avgImpact * 100.0) / 100.0);
        }

        reportNode.set("riskByType", riskNode);
        reportNode.set("impactByType", impactNode);
        reportNode.put("appStability", stabilityStatus);

        resultNode.set("report", reportNode);
        outputs.add(resultNode);

        if ("STABLE".equals(stabilityStatus)) {
            db.setActive(false);
        }
    }

    private String getRiskQualifier(double riskValue) {
        long roundedRisk = Math.round(riskValue);

        if (roundedRisk <= 24) {
            return "NEGLIGIBLE";
        } else if (roundedRisk <= 49) {
            return "MODERATE";
        } else if (roundedRisk <= 74) {
            return "SIGNIFICANT";
        } else {
            return "MAJOR";
        }
    }
}