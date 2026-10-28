"""Evaluación reproducible con tres grupos etiquetados; sin datos ciudadanos."""
import math
import random
from time import perf_counter
from analytics import dbscan


def scenario(size=300, seed=42):
    rng=random.Random(seed)
    centers=[(-16.3567,-72.1875),(-16.349,-72.18),(-16.3665,-72.197)]
    points=[];labels=[]
    n_noise=max(1,size//10)
    for i in range(size-n_noise):
        group=i%3;lat,lng=centers[group]
        radius=math.sqrt(rng.random())*.0012; angle=rng.random()*math.tau
        points.append({'lat':lat+radius*math.sin(angle),'lng':lng+radius*math.cos(angle)})
        labels.append(group)
    for _ in range(n_noise):
        points.append({'lat':rng.uniform(-16.379,-16.336),'lng':rng.uniform(-72.211,-72.161)})
        labels.append(-1)
    return points,labels


def compare(truth,predicted):
    actual={g:{i for i,l in enumerate(truth) if l==g} for g in set(truth)-{-1}}
    found={g:{i for i,l in enumerate(predicted) if l==g} for g in set(predicted)-{-1}}
    # Maximum-cardinality one-to-one matching, with Jaccard >= .5.
    edges={a:sorted([b for b in found if len(actual[a]&found[b])/len(actual[a]|found[b])>=.5]) for a in actual}
    match={}
    def augment(a,seen):
        for b in edges[a]:
            if b in seen:continue
            seen.add(b)
            if b not in match or augment(match[b],seen):
                match[b]=a;return True
        return False
    hits=sum(augment(a,set()) for a in sorted(actual))
    precision=hits/len(found) if found else 0
    recall=hits/len(actual) if actual else 0
    return {'reference_clusters':len(actual),'detected_clusters':len(found),'matched_clusters':hits,
            'unmatched_clusters':len(found)-hits,'missed_clusters':len(actual)-hits,
            'precision':round(precision*100,1),'recall':round(recall*100,1),
            'f1':round(200*precision*recall/(precision+recall),1) if precision+recall else 0,
            'noise_detected':predicted.count(-1),
            'reference_noise':truth.count(-1)}


def evaluate(size=300, eps=250, min_samples=4, seed=42):
    if size not in {100,300,600,1000} or not 50<=eps<=1000 or not 3<=min_samples<=30 or not 0<=seed<=9999:
        raise ValueError('Parámetros de evaluación fuera del rango permitido.')
    points,truth=scenario(size,seed)
    started=perf_counter();predicted=dbscan(points,eps,min_samples)
    elapsed=(perf_counter()-started)*1000
    return {**compare(truth,predicted),'size':size,'eps':eps,'min_samples':min_samples,'seed':seed,
            'elapsed_ms':round(elapsed,2),'points':[dict(p,truth=t,cluster=c) for p,t,c in zip(points,truth,predicted)],
            'method':'Coincidencia uno a uno con Jaccard >= 0.5. Escenario sintético de tres grupos y ruido aleatorio.'}
