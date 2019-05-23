#ifndef CENTROIDTRACKER_HPP
#define CENTROIDTRACKER_HPP

#include <map>
#include <vector>

class CentroidTracker {
public:
	CentroidTracker();
	~CentroidTracker();
	std::map<int, cv::Rect> update(const std::vector<cv::Rect> &boxes);

private:
	void register_object(const cv::Point &centroid);
	void deregister_object(const int objectID);
	std::map<int, cv::Point> allObjectsDisappeared(const std::vector<cv::Rect> &boxes);
	void compute_distances(const std::vector<cv::Point> &XA, const std::vector<cv::Point> &XB);
	std::vector<int> sortRows() const;
	std::vector<int> sortCols(const std::vector<int> rows) const;
	void correlatePositions(const std::vector<int> &objectIDs, const std::vector<cv::Point> &objectCentroids,
		const std::vector<cv::Point> &inputCentroids, std::vector<int> &unusedRows, std::vector<int> &unusedCols,
		std::map<int, cv::Rect> &result, const std::vector<cv::Rect> &boxes);

	int nextObjectID = 0;
	int maxDisappeared = 100;
	std::map<int, cv::Point> objects;
	std::map<int, int> disappeared;
	std::vector<std::vector<double>> dists;
};

#endif // CENTROIDTRACKER_HPP