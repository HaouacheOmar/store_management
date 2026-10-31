// ng serve : /api et /ws vers la gateway (GATEWAY_PORT si 8080 est pris, ex. par Apache).
const gateway = `localhost:${process.env.GATEWAY_PORT ?? 8080}`;

export default {
  '/api': { target: `http://${gateway}`, secure: false },
  '/ws': { target: `ws://${gateway}`, ws: true },
};
