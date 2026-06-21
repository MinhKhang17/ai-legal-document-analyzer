import { auditLogs, documents, knowledgeArticles, processingJobs, projects, reports, riskFindings, systemServices } from './mockData';

const delay = async <T>(value: T, timeout = 260): Promise<T> =>
  new Promise((resolve) => {
    window.setTimeout(() => resolve(value), timeout);
  });

export const mockApi = {
  listProjects: () => delay(projects),
  listDocuments: () => delay(documents),
  listRiskFindings: () => delay(riskFindings),
  listReports: () => delay(reports),
  listAuditLogs: () => delay(auditLogs),
  listKnowledgeArticles: () => delay(knowledgeArticles),
  listSystemServices: () => delay(systemServices),
  listProcessingJobs: () => delay(processingJobs),
};
