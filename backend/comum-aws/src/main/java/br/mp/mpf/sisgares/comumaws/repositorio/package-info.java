/**
 * Repositórios DynamoDB (cliente de baixo nível) de catálogo, configuração e usuário.
 *
 * <p>Todos os acessos usam GetItem, Query ou TransactWriteItems com expressões parametrizadas
 * ({@code ExpressionAttributeValues}/{@code ExpressionAttributeNames}); Scan nunca é usado (Req. 3.9).
 */
package br.mp.mpf.sisgares.comumaws.repositorio;
