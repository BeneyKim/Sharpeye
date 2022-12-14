#include <cmath>
#include <numeric>
#include <opencv2/opencv.hpp>
#include "centroidtracker.hpp"

//#include <android/log.h>

CentroidTracker::CentroidTracker() = default;

CentroidTracker::~CentroidTracker() = default;

/*
** Adds a new object to the list of tracked objects
*/
void CentroidTracker::register_object(const cv::Point2d &centroid, const cv::Rect2f &box) {
	this->objects.insert(std::make_pair(this->nextObjectID, centroid));
	this->lastBoxes.insert(std::make_pair(this->nextObjectID, box));
	this->disappeared.insert(std::make_pair(this->nextObjectID, 0));
	++this->nextObjectID;
}

/*
** Removes an object from the list of tracked objects
*/
void CentroidTracker::deregister_object(const int objectID) {
	this->objects.erase(objectID);
	this->lastBoxes.erase(objectID);
	this->disappeared.erase(objectID);
}

/*
** Calculates the Euclidian distances between each centroids of the tracked objects and each centroids of the new boxes
*/
void CentroidTracker::compute_distances(const std::vector<cv::Point2d> &XA, const std::vector<cv::Point2d> &XB) {
	std::vector<std::vector<double>> dist(XA.size(), std::vector<double>(XB.size()));

	for (int i = 0; i < XA.size(); i++) {
		for (int j = 0; j < XB.size(); j++) {
			dist[i][j] = sqrt( pow(XB[j].x - XA[i].x, 2) + pow(XB[j].y - XA[i].y, 2) );
		}
	}

	this->dists = dist;
}

/*
** Returns a vector of the indexes of each row, sorted by the smallest distances in each row
*/
std::vector<int> CentroidTracker::sortRows() const {
	std::vector<double> mins(this->dists.size());
	for (int i = 0; i < this->dists.size(); i++) {
		mins[i] = *(std::min_element(this->dists[i].begin(), this->dists[i].end()));
	}
	std::vector<int> rows(mins.size());
	std::iota(rows.begin(), rows.end(), 0);
	auto comparator = [&mins](int a, int b){ return mins[a] < mins[b]; };
	std::sort(rows.begin(), rows.end(), comparator);

	return rows;
}

/*
** Returns a vector of the indexes of the smallest distance in each row, sorted by the smallest value
*/
std::vector<int> CentroidTracker::sortCols(const std::vector<int> rows) const {
	std::vector<int> mins(this->dists.size());
	for (int i = 0; i < this->dists.size(); i++) {
		mins[i] = static_cast<int>(std::distance(this->dists[i].begin(), std::min_element(this->dists[i].begin(), this->dists[i].end())));
	}

	std::vector<int> cols;
	for (auto &row: rows) {
		cols.push_back(mins[row]);
	}

	return cols;
}

/*
** Increments the disappeared counter of each object of the list of tracked objects
*/
std::map<int, cv::Point2d> CentroidTracker::allObjectsDisappeared(const std::vector<cv::Rect2f> &boxes) {
	std::vector<int> toDelete;
	for (auto &object: this->disappeared) {
		++(this->disappeared[object.first]);
		if (this->disappeared[object.first] > this->maxDisappeared) {
			toDelete.push_back(object.first);
		}
	}
	for (auto &objectID: toDelete) {
		this->deregister_object(objectID);
	}
	return this->objects;
}

/*
** Updates the centroid of the tracked objects
*/
void CentroidTracker::correlatePositions(
        const std::vector<int> &objectIDs,
        const std::vector<cv::Point2d> &objectCentroids,
        const std::vector<cv::Point2d> &inputCentroids,
        std::vector<int> &unusedRows,
        std::vector<int> &unusedCols,
        const std::vector<cv::Rect2f> &boxes) {
    
	std::vector<int> rows = this->sortRows();
	std::vector<int> cols = this->sortCols(rows);
	std::vector<int> usedRows;
	std::vector<int> usedCols;
	int objectID;
	auto limit = std::min(rows.size(), cols.size());

	for (unsigned int i = 0; i < limit; i++) {
		if (std::find(usedRows.begin(), usedRows.end(), rows[i]) != usedRows.end() ||
			std::find(usedCols.begin(), usedCols.end(), cols[i]) != usedCols.end()) {
			continue;
		}
		objectID = objectIDs[rows[i]];
		this->objects[objectID] = inputCentroids[cols[i]];
		this->lastBoxes[objectID] = boxes[cols[i]];
		this->disappeared[objectID] = 0;
		usedRows.push_back(rows[i]);
		usedCols.push_back(cols[i]);
	}
	for (unsigned int i = 0; i < this->dists.size(); i++)
		if (std::find(usedRows.begin(), usedRows.end(), i) == usedRows.end()) unusedRows.push_back(i);
	for (unsigned int i = 0; i < this->dists[0].size(); i++)
		if (std::find(usedCols.begin(), usedCols.end(), i) == usedCols.end()) unusedCols.push_back(i);
}

/*
** Updates the centroids of the tracked objects and adds/removes objects to the list of tracked objects
*/
std::map<int, cv::Rect2f> CentroidTracker::update(const std::vector<cv::Rect2f> &boxes) {
	std::map<int, cv::Rect2f> res;
	if (boxes.empty()) {
		this->allObjectsDisappeared(boxes);
		return res;
	}

	std::vector<cv::Point2d> inputCentroids;
	for (auto &box: boxes)
		inputCentroids.emplace_back(cv::Point2d(box.x + (box.width / 2.0), box.y + (box.height / 2.0)));

	if (this->objects.empty()) {
		int i = 0;
		for (auto &centroid: inputCentroids) {
			this->register_object(centroid, boxes[i]);
			++i;
		}
	} else {
		std::vector<int> objectIDs;
		std::vector<cv::Point2d> objectCentroids;

		for (auto &object: this->objects) {
			objectIDs.push_back(object.first);
			objectCentroids.push_back(object.second);
		}

		this->compute_distances(objectCentroids, inputCentroids);
		std::vector<int> unusedRows;
		std::vector<int> unusedCols;
		this->correlatePositions(objectIDs, objectCentroids, inputCentroids, unusedRows, unusedCols, boxes);

		int objectID;
		for (auto &row: unusedRows) {
            objectID = objectIDs[row];
            ++(this->disappeared[objectID]);
            if (this->disappeared[objectID] > this->maxDisappeared) {
                this->deregister_object(objectID);
            }
		}
		for (auto &col: unusedCols) {
            this->register_object(inputCentroids[col], boxes[col]);
        }

	}

	for (auto &[objectID, box]: this->lastBoxes) {
		if (this->disappeared[objectID] == 0) {
			res.insert(std::make_pair(objectID, box));
		}
	}
	return res;
}