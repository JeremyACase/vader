/**
 * TypeScript equivalent of org.vader.common.model.vader.dto.ClientPrompt.
 * `id`, `createdAt`, and `updatedAt` are populated by the server
 * (see AbstractModel.java) and are absent on a client-submitted prompt.
 */
export interface ClientPrompt {
  id?: string;
  createdAt?: string;
  updatedAt?: string;
  readonly modelType: 'ClientPrompt';
  text: string;
  files?: File[];
}
