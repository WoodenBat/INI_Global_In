package com.ini.board.service;

import java.io.File;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.ini.board.mapper.BoardMapper;
import com.ini.board.vo.BoardCategoryVO;
import com.ini.board.vo.BoardDTO;
import com.ini.board.vo.BoardDetailDTO;
import com.ini.board.vo.BoardImageDTO;
import com.ini.board.vo.BoardLikeDTO;
import com.ini.board.vo.BoardListDTO;
import com.ini.board.vo.BoardReportDTO;
import com.ini.board.vo.BoardTagVO;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class BoardService {

	private final BoardMapper boardMapper;

	@Value("${file.upload-path}")
	private String uploadPath;

	public List<BoardDTO> getBoardList(String keyword, String category, int page, int pageSize) {
		int startRow = (page - 1) * pageSize;
		return boardMapper.selectBoardList(keyword, category, startRow, pageSize);
	}

	public int getBoardCount(String keyword, String category) {
		return boardMapper.countBoardList(keyword, category);
	}

	public int saveBoard(BoardDTO board) {
		boardMapper.insertBoard(board);

		return board.getBoard_id();
	}

//	public BoardDTO getBoardById(int board_id) {
//
//		BoardDTO board = boardMapper.getBoardById(board_id);
//
//		return board;
//	}

	public void savePost(BoardDTO boardDTO) {
		boardMapper.insertPost(boardDTO);
		if (boardDTO.getImageList() != null && !boardDTO.getImageList().isEmpty()) {
			saveImages(boardDTO.getImageList()); // 변경
		}
	}

	// 말머리 이름 목록 반환
	public List<String> getHeadList() {
		return boardMapper.selectAllBoardTags().stream().map(BoardTagVO::getBoard_tag).toList();
	}

	// 카테고리 이름 목록 반환
	public List<String> getCategoryList() {
		return boardMapper.selectAllBoardCategories().stream().map(BoardCategoryVO::getBoard_category).toList();
	}

	// 전체 말머리 VO 객체 목록 반환
	public List<BoardTagVO> getAllTags() {
		return boardMapper.selectAllBoardTags();
	}

	// 전체 카테고리 VO 객체 목록 반환
	public List<BoardCategoryVO> getAllCategories() {
		return boardMapper.selectAllBoardCategories();
	}

	/**
	 * 게시글 목록 조회 조건 없이 최근 10개 가져오는 테스트용 쿼리
	 */
	public List<BoardListDTO> getPostList(String keyword, String category, String tag, int offset, int limit) {
		return boardMapper.getBoardList(keyword, category, tag, offset, limit);
	}

	public int getTotalPostCount() {
		return boardMapper.countAllPosts(); // BoardMapper에 매핑 필요
	}

	public int getPostCount(String keyword, String category, String tag) {
		return boardMapper.getPostCount(keyword, category, tag);
	}

	// 게시글 상세 조회 (본문 + 유저정보 등)
	public BoardDetailDTO getPostById(int id) {
		return boardMapper.selectPostById(id);
	}

	// 단순 게시글 DTO만 조회
	public BoardDetailDTO getBoardById(int boardId, String userId) {
		BoardDetailDTO detail = boardMapper.selectPostById(boardId);
		if (detail == null)
			return null;

		int likeCount = boardMapper.getLikeCount((long) boardId);
		detail.setLike_count(likeCount);

		boolean liked = boardMapper.hasUserLiked(boardId, userId) != null;
		detail.setLiked(liked);

		// 📌 이미지 목록 추가
		List<BoardImageDTO> images = boardMapper.selectImageList(boardId);
		detail.setImageList(images);

		return detail;
	}

	// 게시글 수정 처리
	public void updatePost(BoardDTO dto) {
		// 게시글 본문에 포함된 이미지 파일명 목록 추출
		List<String> newImageNames = dto.getImageList().stream().map(BoardImageDTO::getImage_path).toList();

		// 기존 이미지 목록 가져오기
		List<BoardImageDTO> existingImages = boardMapper.selectImageList(dto.getBoard_id());

		// 1. 삭제 대상: 기존에는 있었지만, 이제는 없는 이미지들만 삭제
		for (BoardImageDTO existing : existingImages) {
			if (!newImageNames.contains(existing.getImage_path())) {
				// 파일 삭제
				File file = new File(uploadPath, existing.getImage_path());
				if (file.exists()) {
					file.delete();
				}
				// DB 삭제
				boardMapper.deleteImageByPath(existing.getBoard_id(), existing.getImage_path());
			}
		}

		// 2. 게시글 정보 업데이트
		boardMapper.updatePost(dto);

		// 3. DB에는 없는 새 이미지만 insert
		for (BoardImageDTO image : dto.getImageList()) {
			boolean alreadyExists = existingImages.stream()
					.anyMatch(e -> e.getImage_path().equals(image.getImage_path()));

			if (!alreadyExists) {
				boardMapper.insertBoardImage(image);
			}
		}
	}

	// 게시글 삭제 처리 (좋아요, 댓글, 이미지 포함)
	public void deletePost(int boardId) {
		boardMapper.deleteLikesByBoardId(boardId);
		boardMapper.deleteCommentsByBoardId(boardId);
		boardMapper.deleteImagesByBoardId(boardId);
		boardMapper.deletePost(boardId);
	}

	// 조회수 증가
	public void incrementViews(int boardId) {
		boardMapper.updateViewCount(boardId);
	}

	/**
	 * 좋아요 토글 기능 이미 눌렀으면 취소, 아니면 좋아요 등록 결과적으로 현재 좋아요 수 반환
	 */
	public int toggleLike(int boardId, String userId) {
		boolean hasLiked = hasUserLiked(boardId, userId);
		if (hasLiked) {
			boardMapper.deleteLike(boardId, userId);
		} else {
			BoardLikeDTO like = new BoardLikeDTO(boardId, userId);
			boardMapper.insertLike(like);
		}
		return boardMapper.countLikes(boardId);
	}

	// 좋아요 수 반환
	public int getLikeCount(int boardId) {
		return boardMapper.countLikes(boardId);
	}

	// 특정 유저가 특정 게시글에 좋아요 눌렀는지 확인
	public boolean hasUserLiked(int boardId, String userId) {
		if (userId == null || userId.trim().isEmpty()) {
			userId = "test"; // 로그인 안 된 경우 대비 기본값
		}
		Integer count = boardMapper.hasUserLiked(boardId, userId);
		return count != null && count > 0;
	}

	// 특정 게시글의 이미지 목록 조회
	public List<BoardImageDTO> getBoardImages(int boardId) {
		return boardMapper.selectImageList(boardId);
	}

	// ✅ 단독 이미지 저장용 메서드 (예: 작성 후 저장 or 수정 시 추가 저장)
	public void saveImages(List<BoardImageDTO> imageList) {
		if (imageList != null && !imageList.isEmpty()) {
			for (BoardImageDTO img : imageList) {
				boardMapper.insertBoardImage(img);
			}
		}
	}

	public BoardDetailDTO getBoardById(int boardId) {
		return getBoardById(boardId, "test");
	}

	public void deleteImagesByBoardId(int boardId) {
		List<BoardImageDTO> images = boardMapper.selectImageList(boardId);

		for (BoardImageDTO img : images) {
			File file = new File(uploadPath, img.getImage_path());
			if (file.exists()) {
				boolean deleted = file.delete();
				if (!deleted) {
					// 혹시 삭제 실패 로그 출력해도 좋습니다.
					System.out.println("파일 삭제 실패: " + file.getAbsolutePath());
				}
			}
		}
		boardMapper.deleteImagesByBoardId(boardId);
	}

	// 신고 등록
	public boolean addReport(BoardReportDTO report) {
		// 중복신고 확인
		int count = boardMapper.countReportsByBoardAndUser(report.getBoard_id(), report.getReport_user());
		if (count > 0) {
			return false; // 이미 신고한 경우
		}
		int result = boardMapper.insertBoardReport(report);
		return result > 0;
	}

	// 게시글 신고 횟수 조회
	public int getReportCount(int boardId) {
		return boardMapper.countReportsByBoard(boardId);
	}

	// 🔹 end 제거된 메서드
	public List<BoardDTO> getBoardList2(String keyword, String category, int offset, int limit) {
		return boardMapper.selectBoardList2(keyword, category, offset, limit);
	}

	// 🔸 게시글 총 개수 조회
	public int countBoards(String keyword, String category) {
		return boardMapper.countBoards(keyword, category);
	}
}
